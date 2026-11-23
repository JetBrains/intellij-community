// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.collaboration.async.cancelledWith
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.asObservableIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindValidationOnApplyIn
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.authentication.CustomServer
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent

/**
 * Shows a dialog to add or edit a [CustomServer].
 */
@Suppress("SplitModeApiUsage")
internal class GitLabConfigureCustomServerDialog private constructor(
  parentCs: CoroutineScope,
  project: Project,
  parentComponent: JComponent,
  isNew: Boolean,
  private val vm: GitLabCustomServerConfigurationEditorViewModel,
) : DialogWrapper(project, parentComponent, false, IdeModalityType.IDE) {
  private val cs = parentCs.childScope(javaClass.name).cancelledWith(disposable)

  init {
    title = if (isNew) GitLabBundle.message("settings.oauth.custom.server.add.title")
    else GitLabBundle.message("settings.oauth.custom.server.edit.title")
    init()
  }

  override fun createCenterPanel(): DialogPanel = panel {
    row(CollaborationToolsBundle.message("login.field.server")) {
      textField()
        .bindTextIn(cs, vm.serverUri.valueFlow)
        .bindValidationOnApplyIn(cs, vm.serverUri)
        .align(AlignX.FILL)
        .resizableColumn()
        .focused()
    }
    row(GitLabBundle.message("account.oauth.client.id.label")) {
      textField()
        .bindTextIn(cs, vm.clientId.valueFlow)
        .bindValidationOnApplyIn(cs, vm.clientId)
        .comment(GitLabBundle.message("settings.oauth.custom.server.client.id.hint"))
        .enabledIf(vm.isServerGitlabDotCom.mapState { !it }.asObservableIn(cs))
        .align(AlignX.FILL)
        .resizableColumn()
    }
    row(GitLabBundle.message("settings.oauth.custom.server.root.namespace.id.label")) {
      textField()
        .bindTextIn(cs, vm.rootNamespaceId.valueFlow)
        .bindValidationOnApplyIn(cs, vm.rootNamespaceId)
        .comment(GitLabBundle.message("settings.oauth.custom.server.root.namespace.id.hint"))
        .align(AlignX.FILL)
        .resizableColumn()
    }
    row("") {
      addWarningForHttpProtocol(cs, vm.isHttpUri).align(AlignX.LEFT)
    }
  }.withPreferredWidth(400)

  companion object {
    /**
     * Returns the entered configured server, or null when the user cancels the dialog.
     *
     * @param serverToEdit the configured server to edit, or null to add a new one.
     */
    fun show(
      cs: CoroutineScope,
      project: Project,
      parent: JComponent,
      serverToEdit: CustomServer?,
    ): CustomServer? {
      val vm = GitLabCustomServerConfigurationEditorViewModel(serverToEdit)
      val dialog = GitLabConfigureCustomServerDialog(cs, project, parent, serverToEdit == null, vm)
      return if (dialog.showAndGet()) vm.buildServer() else null
    }
  }
}
