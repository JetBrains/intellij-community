// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config.gpg

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.VcsException
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InlineBanner
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.FontUtil
import com.intellij.util.ui.NamedColorUtil
import git4idea.GitUtil
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import kotlin.properties.Delegates

class GitGpgConfigDialog(
  private val repository: GitRepository,
  val secretKeys: SecretKeysValue,
  private val repoConfig: RepoConfigValue
) : DialogWrapper(repository.project) {

  private val uiDispatcher get() = Dispatchers.EDT + ModalityState.any().asContextElement()
  private val scope = CoroutineScope(SupervisorJob()).also { Disposer.register(disposable) { it.cancel() } }

  private lateinit var checkBox: JCheckBox
  private lateinit var comboBox: ComboBox<GpgKey?>

  private lateinit var loadingIcon: Cell<JLabel>
  private lateinit var modifiedWarningBanner: Cell<InlineBanner>
  private var modifiedWarningText: @Nls String? = null
  private lateinit var errorLabel: Cell<JLabel>
  private lateinit var docLinkLabel: Cell<ActionLink>

  private var isLoading: Boolean by Delegates.observable(false) { _, _, _ -> updatePresentation() }
  private var hasLoadedKeys: Boolean by Delegates.observable(false) { _, _, _ -> updatePresentation() }
  private var initialConfig: RepoConfig? = null

  init {
    title = message("settings.configure.sign.gpg.for.repo.dialog.title", GitUtil.mention(repository))

    init()
    initComboBox()
    updatePresentation()
  }

  override fun getPreferredFocusedComponent(): JComponent = checkBox

  override fun createCenterPanel(): JComponent =
    panel {
      row {
        checkBox = checkBox(message("settings.configure.sign.gpg.with.key.checkbox.text"))
          .applyToComponent { addChangeListener { updatePresentation() } }
          .component
      }
      indent {
        row {
          comboBox = comboBox(listOf(), MyComboboxRenderer())
            .columns(COLUMNS_MEDIUM)
            .applyToComponent { addItemListener { updatePresentation() } }
            .validationOnApply {
              if (checkBox.isSelected && it.item == null) error(message("settings.configure.sign.gpg.for.repo.dialog.keys.validation.text"))
              else null
            }
            .component
          loadingIcon = icon(AnimatedIcon.Default())
            .visible(false)
        }
      }
      row {
        modifiedWarningBanner = cell(InlineBanner(status = EditorNotificationPanel.Status.Info))
          .align(AlignX.FILL)
          .visible(false)
          .applyToComponent { showCloseButton(false) }
      }
      row {
        errorLabel = label("")
          .visible(false)
          .applyToComponent { foreground = NamedColorUtil.getErrorForeground() }
      }
      row {
        docLinkLabel = link(message("gpg.error.see.documentation.link.text")) {
          HelpManager.getInstance().invokeHelp(message("gpg.jb.manual.link"))
        }.visible(false)
      }
    }

  private fun initComboBox() {
    launch("loading config") {
      val comboBoxModel = comboBox.model as DefaultComboBoxModel

      val config = repoConfig.load()
      checkBox.isSelected = config.isEnabled
      if (config.key != null) {
        comboBoxModel.selectedItem = config.key
      }
      initialConfig = config
      updatePresentation()

      val keys = secretKeys.load()
      comboBoxModel.addAll(keys.keys)

      if (keys.keys.isEmpty()) {
        reportError(message("settings.configure.sign.gpg.error.no.available.keys.found.text"))
      }
      else {
        hasLoadedKeys = true
      }
    }
  }

  private fun updatePresentation() {
    loadingIcon.visible(isLoading)
    checkBox.isEnabled = !isLoading
    comboBox.isEnabled = !isLoading && checkBox.isSelected
    isOKActionEnabled = !isLoading && (hasLoadedKeys && comboBox.item != null || !checkBox.isSelected)
    updateModifiedWarning()
  }

  private fun updateModifiedWarning() {
    val modifiedKey = getModifiedKey()
    val modifiedIsEnabled = getModifiedIsEnabled()

    val commands = listOfNotNull(
      modifiedIsEnabled?.let { message("settings.configure.sign.gpg.modified.warning.gpgSign.command", it) },
      modifiedKey?.let { message("settings.configure.sign.gpg.modified.warning.signingKey.command", it.id) },
    )
    val warningText = if (!isOKActionEnabled || commands.isEmpty()) null
    else HtmlBuilder()
      .append(message("settings.configure.sign.gpg.modified.warning.head", commands.size))
      .apply { commands.forEach { append(HtmlChunk.br()).append(HtmlChunk.text(it).code()) } }
      .toString()

    if (modifiedWarningText == warningText) return
    modifiedWarningText = warningText

    if (warningText != null) modifiedWarningBanner.component.setMessage(warningText)
    modifiedWarningBanner.visible(warningText != null)
    if (isShowing) pack()
  }

  private fun getModifiedKey(): GpgKey? {
    val initialConfig = initialConfig ?: return null
    if (!checkBox.isSelected) return null
    return comboBox.item.takeIf { initialConfig.key != it }
  }

  private fun getModifiedIsEnabled(): Boolean? {
    val initialConfig = initialConfig ?: return null
    return checkBox.isSelected.takeIf { initialConfig.isEnabled != it }
  }

  override fun doOKAction() {
    val newGpgKey = getModifiedKey()
    val newIsEnabled = getModifiedIsEnabled()
    if (newGpgKey == null && newIsEnabled == null) {
      close(OK_EXIT_CODE)
      return
    }
    launch("writing result") {
      writeGitSettings(newGpgKey, newIsEnabled)
      repoConfig.reload()
      close(OK_EXIT_CODE)
    }
  }

  private fun launch(action: @NonNls String, block: suspend CoroutineScope.() -> Unit) {
    scope.launch(uiDispatcher +
                 CoroutineName("GitDefineGpgConfigDialog - $action")) {
      isLoading = true
      try {
        block()
      }
      catch (e: VcsException) {
        logger<GitGpgConfigDialog>().warn(e)
        reportError(e.message)
      }
      finally {
        isLoading = false
      }
    }
  }

  private fun reportError(message: @Nls String) {
    errorLabel.component.text = message
    errorLabel.visible(true)
    docLinkLabel.visible(true)
  }

  @Throws(VcsException::class)
  private suspend fun writeGitSettings(gpgKey: GpgKey?, isEnabled: Boolean?) {
    withContext(Dispatchers.IO) {
      coroutineToIndicator { writeGitGpgConfig(repository, gpgKey, isEnabled) }
    }
  }

  private inner class MyComboboxRenderer : ColoredListCellRenderer<GpgKey?>() {
    override fun customizeCellRenderer(list: JList<out GpgKey>, value: GpgKey?, index: Int, selected: Boolean, hasFocus: Boolean) {
      if (value == null) return

      append(value.id)

      val descriptions = secretKeys.value?.descriptions ?: return
      val description = descriptions[value]
      if (description != null) {
        append(FontUtil.spaceAndThinSpace())
        append(description, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }
  }
}
