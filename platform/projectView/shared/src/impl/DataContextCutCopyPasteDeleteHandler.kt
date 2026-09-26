// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.impl

import com.intellij.ide.CopyPasteDelegator
import com.intellij.ide.projectView.impl.ProjectViewDeleteElementProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import javax.swing.JPanel

/**
 * The backend half of the Project View cut/copy/paste/delete support: everything that needs PSI.
 *
 * The frontend counterpart (`FrontendProjectViewCutCopyPasteDeleteProvider`) only knows the selected node IDs and
 * sends them here over the pane request channel, so nothing in this file may depend on the frontend data
 * context. The context these functions take is the one the pane model builds locally out of those node
 * IDs, because the platform handlers underneath are all data-context based.
 *
 * Each operation re-checks its own enabled state, because the frontend can only approximate it: it has no
 * PSI, so it reports "enabled" for any non-empty selection. An operation that isn't actually possible
 * therefore ends up as a no-op here.
 *
 * Everything runs on EDT under the write-intent read lock, the way the monolith Project View runs it from
 * the action itself: copying touches the clipboard and notifies listeners, and pasting and deleting show
 * refactoring and confirmation dialogs.
 */
@ApiStatus.Experimental
object DataContextCutCopyPasteDeleteHandler {

  suspend fun copy(dataContext: DataContext) {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return
    writeIntentReadAction {
      val provider = createDelegator(project).copyProvider
      if (provider.isCopyEnabled(dataContext)) {
        provider.performCopy(dataContext)
      }
    }
  }

  suspend fun cut(dataContext: DataContext) {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return
    writeIntentReadAction {
      val provider = createDelegator(project).cutProvider
      if (provider.isCutEnabled(dataContext)) {
        provider.performCut(dataContext)
      }
    }
  }

  suspend fun paste(dataContext: DataContext) {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return
    writeIntentReadAction {
      val provider = createDelegator(project).pasteProvider
      if (provider.isPasteEnabled(dataContext)) {
        provider.performPaste(dataContext)
      }
    }
  }

  /** Deletes using the provider the given context supplies, the way [com.intellij.ide.actions.DeleteAction] does. */
  suspend fun delete(dataContext: DataContext) {
    writeIntentReadAction {
      val provider = PlatformDataKeys.DELETE_ELEMENT_PROVIDER.getData(dataContext) ?: return@writeIntentReadAction
      if (provider.canDeleteElement(dataContext)) {
        provider.deleteElement(dataContext)
      }
    }
  }

  /**
   * The delegator is stateless apart from the component, which it only uses to repaint after the
   * operation. The Project View repaints itself in response to the resulting PSI/clipboard changes, so a
   * throwaway panel is enough (the same trick the remote-dev Project View backend uses).
   */
  private fun createDelegator(project: Project): CopyPasteDelegator = CopyPasteDelegator(project, JPanel())
}

/**
 * The Project View delete provider for the new pane model: same behavior as the one
 * [com.intellij.ide.projectView.impl.AbstractProjectViewPane] installs, except that the elements come
 * from the node IDs the frontend sent rather than from the data context.
 */
internal class ProjectViewNodeDeleteProvider(
  private val elements: List<PsiElement>,
  private val isHideEmptyMiddlePackages: Boolean,
) : ProjectViewDeleteElementProvider() {
  override fun getSelectedPSIElements(dataContext: DataContext): Array<PsiElement> = elements.toTypedArray()

  override fun hideEmptyMiddlePackages(dataContext: DataContext): Boolean = isHideEmptyMiddlePackages
}
