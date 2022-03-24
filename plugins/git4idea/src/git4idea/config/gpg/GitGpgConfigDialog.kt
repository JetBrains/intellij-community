// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config.gpg

import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.progress.runUnderIndicator
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsException
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.layout.*
import com.intellij.util.FontUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
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
  val repoConfig: RepoConfigValue
) : DialogWrapper(repository.project) {
  private val uiDispatcher get() = AppUIExecutor.onUiThread(ModalityState.any()).coroutineDispatchingContext()
  private val scope = CoroutineScope(SupervisorJob()).also { Disposer.register(disposable) { it.cancel() } }

  private val checkBox = JCheckBox(message("settings.configure.sign.gpg.with.key.checkbox.text")).apply {
    addChangeListener { updatePresentation() }
  }
  private val comboBox = ComboBox<GpgKey>().apply {
    renderer = MyComboboxRenderer()
  }
  private val loadingIcon = JBLabel(AnimatedIcon.Default()).apply {
    isVisible = false
  }
  private val errorLabel = JBLabel().apply {
    foreground = UIUtil.getErrorForeground()
    isVisible = false
  }
  private val docLinkLabel = LinkLabel<String>(message("gpg.error.see.documentation.link.text"),
                                               null,
                                               LinkListener { _, _ ->
                                                 HelpManager.getInstance().invokeHelp(message("gpg.jb.manual.link"))
                                               })
    .apply {
      isVisible = false
    }

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
        checkBox()
        row {
          comboBox()
            .growPolicy(GrowPolicy.MEDIUM_TEXT)
          loadingIcon()
        }
      }
      row {
        errorLabel()
      }
      row {
        docLinkLabel()
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
    loadingIcon.isVisible = isLoading
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
    errorLabel.text = message
    errorLabel.isVisible = true
    docLinkLabel.isVisible = true
  }

  @Throws(VcsException::class)
  private suspend fun writeGitSettings(gpgKey: GpgKey?) {
    withContext(Dispatchers.IO) {
      runUnderIndicator { writeGitGpgConfig(repository, gpgKey) }
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
