// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting.action

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier
import javax.swing.Icon

abstract class HostedGitRepositoryReferenceActionGroup : ActionGroup, DumbAware {

  constructor() : super()

  constructor(dynamicText: Supplier<@NlsActions.ActionText String>,
              dynamicDescription: Supplier<@NlsActions.ActionDescription String>,
              icon: Supplier<Icon?>?)
    : super(dynamicText, dynamicDescription, icon)

  @ApiStatus.ScheduledForRemoval
  @Deprecated(level = DeprecationLevel.ERROR, message = "Use icon supplier")
  constructor(dynamicText: Supplier<@NlsActions.ActionText String>,
              dynamicDescription: Supplier<@NlsActions.ActionDescription String>,
              icon: Icon?)
    : super(dynamicText, dynamicDescription, icon)

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  override fun update(e: AnActionEvent) {
    val references = findReferences(e.dataContext).distinct()
    e.presentation.isEnabledAndVisible = references.isNotEmpty()
    e.presentation.isPerformGroup = references.size == 1
    e.presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, e.presentation.isPerformGroup);
    e.presentation.isPopupGroup = true
    e.presentation.isDisableGroupIfEmpty = false
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    if (e == null) return emptyArray()
    val references = findReferences(e.dataContext).distinct()
    if (references.size <= 1) return emptyArray()

    return references.map { OpenInBrowserAction(it) }.toTypedArray()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val reference = findReferences(e.dataContext).firstOrNull() ?: return
    OpenInBrowserAction(reference).actionPerformed(e)
  }

  protected abstract fun findReferences(dataContext: DataContext): List<HostedGitRepositoryReference>

  protected abstract fun handleReference(reference: HostedGitRepositoryReference)

  private inner class OpenInBrowserAction(private val reference: HostedGitRepositoryReference) : DumbAwareAction({ reference.getName() }) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
      handleReference(reference)
    }
  }
}