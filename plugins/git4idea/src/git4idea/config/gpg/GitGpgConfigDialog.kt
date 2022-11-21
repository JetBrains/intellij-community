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
import com.intellij.openapi.vcs.VcsException
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.FontUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import git4idea.GitUtil
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitRepository
import kotlinx.coroutines.*
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.*
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
  private lateinit var errorLabel: Cell<JLabel>
  private lateinit var docLinkLabel: Cell<ActionLink>

  private var isLoading: Boolean by Delegates.observable(false) { _, _, _ -> updatePresentation() }
  private var hasLoadedKeys: Boolean by Delegates.observable(false) { _, _, _ -> updatePresentation() }

  init {
    title = message("settings.configure.sign.gpg.for.repo.dialog.title", GitUtil.mention(repository))

    init()
    initComboBox()
    updatePresentation()
  }

  override fun getPreferredFocusedComponent(): JComponent = checkBox

  override fun createSouthAdditionalPanel(): JPanel {
    val label = JBLabel(message("settings.configure.sign.gpg.synced.with.gitconfig.text"))
    label.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    return JBUI.Panels.simplePanel(label)
  }

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
            .component
          loadingIcon = icon(AnimatedIcon.Default())
            .visible(false)
        }
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
      checkBox.isSelected = config.key != null
      if (config.key != null) {
        comboBoxModel.selectedItem = config.key
      }

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
    isOKActionEnabled = !isLoading && (hasLoadedKeys || !checkBox.isSelected)
  }

  override fun doOKAction() {
    launch("writing result") {
      writeGitSettings(if (checkBox.isSelected) comboBox.item else null)
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
  private suspend fun writeGitSettings(gpgKey: GpgKey?) {
    withContext(Dispatchers.IO) {
      coroutineToIndicator { writeGitGpgConfig(repository, gpgKey) }
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
