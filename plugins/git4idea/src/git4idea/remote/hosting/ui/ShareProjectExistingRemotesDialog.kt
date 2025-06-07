// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Container
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

@ApiStatus.Internal
class ShareProjectExistingRemotesDialog(
  project: Project,
  private val hostServiceName: @NlsContexts.ConfigurableName String,
  private val remotes: List<String>
) : DialogWrapper(project) {
  init {
    title = GitBundle.message("share.error.project.is.on.host", hostServiceName)
    setOKButtonText(GitBundle.message("share.anyway.button"))
    init()
  }

  override fun createCenterPanel(): JComponent {
    val mainText = JBLabel(if (remotes.size == 1) GitBundle.message("share.action.remote.is.on.host", hostServiceName)
                           else GitBundle.message("share.action.remotes.are.on.host", hostServiceName))

    val remotesPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    for (remote in remotes) {
      remotesPanel.add(BrowserLink(remote, remote))
    }

    val messagesPanel = JBUI.Panels.simplePanel(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP)
      .addToTop(mainText)
      .addToCenter(remotesPanel)

    val iconContainer = Container().apply {
      layout = BorderLayout()
      add(JLabel(Messages.getQuestionIcon()), BorderLayout.NORTH)
    }
    return JBUI.Panels.simplePanel(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP)
      .addToCenter(messagesPanel)
      .addToLeft(iconContainer)
      .apply { border = JBUI.Borders.emptyBottom(UIUtil.LARGE_VGAP) }
  }
}