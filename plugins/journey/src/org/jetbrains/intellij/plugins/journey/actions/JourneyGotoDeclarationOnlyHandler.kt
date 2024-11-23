// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.plugins.journey.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.navigation.actions.navigateRequest
import com.intellij.codeInsight.navigation.impl.*
import com.intellij.codeInsight.navigation.impl.NavigationActionResult.MultipleTargets
import com.intellij.codeInsight.navigation.impl.NavigationActionResult.SingleTarget
import com.intellij.ide.util.EditSourceUtil
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ex.ActionUtil.underModalProgress
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.DumbModeBlockedFunctionality
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.list.createTargetPopup
import com.intellij.util.ui.EDT
import org.jetbrains.intellij.plugins.journey.JourneyDataKeys

internal class JourneyGotoDeclarationOnlyHandler() : CodeInsightActionHandler {

  companion object {

    fun navigateToLookupItem(project: Project): Boolean {
      val activeLookup: Lookup? = LookupManager.getInstance(project).activeLookup
      if (activeLookup == null) {
        return false
      }
      val currentItem = activeLookup.currentItem
      navigateRequestLazy(project) {
        TargetElementUtil.targetElementFromLookupElement(currentItem)
          ?.gtdTargetNavigatable()
          ?.navigationRequest()
      }
      return true
    }

    internal fun PsiElement.gtdTargetNavigatable(): Navigatable? {
      return TargetElementUtil.getInstance()
        .getGotoDeclarationTarget(this, navigationElement)
        ?.psiNavigatable()
    }

    internal fun PsiElement.psiNavigatable(): Navigatable? {
      return this as? Navigatable
             ?: EditSourceUtil.getDescriptor(this)
    }

    /**
     * Obtains a [NavigationRequest] instance from [requestor] on a background thread, and calls [navigateRequest].
     */
    fun navigateRequestLazy(project: Project, requestor: NavigationRequestor) {
      EDT.assertIsEdt()
      @Suppress("DialogTitleCapitalization")
      val request = underModalProgress(project, ActionsBundle.actionText("GotoDeclarationOnly")) {
        requestor.navigationRequest()
      }
      if (request != null) {
        navigateRequest(project, request)
      }
    }

    private fun gotoDeclaration(project: Project, editor: Editor, file: PsiFile, offset: Int): GTDActionData? {
      return fromGTDProviders(project, editor, offset)
             ?: gotoDeclaration(file, offset)
    }

    internal fun gotoDeclaration(
      project: Project,
      editor: Editor,
      actionResult: NavigationActionResult,
    ) {
      when (actionResult) {
        is SingleTarget -> {
          val diagramDataModel = editor.getUserData(JourneyDataKeys.JOURNEY_DIAGRAM_DATA_MODEL)
          if (diagramDataModel != null) {
            val navigationRequest = underModalProgress(project, "Journey goto declaration.") {
              actionResult.requestor.navigationRequest()
            }

            diagramDataModel.addEdge(editor, navigationRequest)
            return
          }
          navigateRequestLazy(project, actionResult.requestor)
        }
        is MultipleTargets -> {
          val popup = createTargetPopup(
            CodeInsightBundle.message("declaration.navigation.title"),
            actionResult.targets, LazyTargetWithPresentation::presentation
          ) { (requestor, _, _) ->
            navigateRequestLazy(project, requestor)
          }
          popup.showInBestPositionFor(editor)
        }
      }
    }
  }

  override fun startInWriteAction(): Boolean = false

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    if (navigateToLookupItem(project)) {
      return
    }

    if (EditorUtil.isCaretInVirtualSpace(editor)) {
      return
    }

    val offset = editor.caretModel.offset
    val actionResult: NavigationActionResult? = try {
      underModalProgress(project, CodeInsightBundle.message("progress.title.resolving.reference")) {
        gotoDeclaration(project, editor, file, offset)?.result()
      }
    }
    catch (_: IndexNotReadyException) {
      DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
        CodeInsightBundle.message("popup.content.navigation.not.available.during.index.update"),
        DumbModeBlockedFunctionality.GotoDeclarationOnly)
      return
    }

    if (actionResult == null) {
      //notifyNowhereToGo(project, editor, file, offset)
      throw UnsupportedOperationException("Journey TODO")
    }
    else {
      gotoDeclaration(project, editor, actionResult)
    }
  }

}
