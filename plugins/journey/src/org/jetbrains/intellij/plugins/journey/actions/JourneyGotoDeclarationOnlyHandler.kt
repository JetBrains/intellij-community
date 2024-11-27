// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.plugins.journey.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
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
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.list.createTargetPopup
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.plugins.journey.JourneyDataKeys
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramDataModel

internal class JourneyGotoDeclarationOnlyHandler() : CodeInsightActionHandler {

  companion object {

    @ApiStatus.Internal
    fun findDeclarations(
      editor: Editor,
      diagramDataModel: JourneyDiagramDataModel,
      project: Project,
      file: PsiFile,
    ): NavigationActionResult? {
      val actionResult: NavigationActionResult?

      if (navigateToLookupItem(editor, diagramDataModel)) {
        actionResult = null
      }
      else

        if (EditorUtil.isCaretInVirtualSpace(editor)) {
          actionResult = null
        }
        else {
          val offset = editor.caretModel.offset
          actionResult = try {
            underModalProgress(project, CodeInsightBundle.message("progress.title.resolving.reference")) {
              gotoDeclaration(project, editor, file, offset)?.result()
            }
          }
          catch (_: IndexNotReadyException) {
            DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
              CodeInsightBundle.message("popup.content.navigation.not.available.during.index.update"),
              DumbModeBlockedFunctionality.GotoDeclarationOnly)
            null
          }
        }
      return actionResult
    }

    fun navigateToLookupItem(editor: Editor, model: JourneyDiagramDataModel): Boolean {
      val project = editor.project
      if (project == null) {
        return false
      }

      val activeLookup: Lookup? = LookupManager.getInstance(project).activeLookup
      if (activeLookup == null) {
        return false
      }
      val currentItem = activeLookup.currentItem
      val targetElementFromLookupElement = TargetElementUtil.targetElementFromLookupElement(currentItem)

      navigateRequestLazy(project, editor, model) {
        targetElementFromLookupElement
          ?.gtdTargetNavigatable()
          ?.navigationRequest()
      }
      return true
    }

    internal fun PsiElement.gtdTargetNavigatable(): Navigatable? = TargetElementUtil.getInstance()
      .getGotoDeclarationTarget(this, navigationElement)
      ?.psiNavigatable()

    fun PsiElement.psiNavigatable(): Navigatable? = this as? Navigatable ?: EditSourceUtil.getDescriptor(this)

    fun navigateRequestLazy(project: Project, editor: Editor, diagramDataModel: JourneyDiagramDataModel, requestor: NavigationRequestor) {
      EDT.assertIsEdt()
      @Suppress("DialogTitleCapitalization")
      val request = underModalProgress(project, ActionsBundle.actionText("GotoDeclarationOnly")) {
        requestor.navigationRequest()
      }
      if (request != null) {
        diagramDataModel.addEdgeAsync(editor, request)
      }
    }

    private fun gotoDeclaration(project: Project, editor: Editor, file: PsiFile, offset: Int): GTDActionData? {
      return fromGTDProviders(project, editor, offset)
             ?: gotoDeclaration(file, offset)
    }

    @ApiStatus.Internal
    fun gotoDeclaration(
      project: Project,
      editor: Editor,
      actionResult: NavigationActionResult,
      model: JourneyDiagramDataModel,
    ) {
      when (actionResult) {
        is SingleTarget -> {
          navigateRequestLazy(project, editor, model, actionResult.requestor)
        }
        is MultipleTargets -> {
          // never tested. not sure if used at all.
          val popup = createTargetPopup(
            CodeInsightBundle.message("declaration.navigation.title"),
            actionResult.targets, LazyTargetWithPresentation::presentation
          ) { (requestor, _, _) ->
            navigateRequestLazy(project, editor, model, requestor)
          }
          popup.showInBestPositionFor(editor)
        }
      }
    }
  }

  override fun startInWriteAction(): Boolean = false

  @RequiresEdt
  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val diagramDataModel = editor.getUserData(JourneyDataKeys.JOURNEY_DIAGRAM_DATA_MODEL)
    if (diagramDataModel == null) {
      return
    }

    val actionResult: NavigationActionResult? = findDeclarations(editor, diagramDataModel, project, file)

    if (actionResult == null) {
      //notifyNowhereToGo(project, editor, file, offset)
      throw UnsupportedOperationException("Journey TODO")
    }
    else {
      gotoDeclaration(project, editor, actionResult, diagramDataModel)
    }
  }

}
