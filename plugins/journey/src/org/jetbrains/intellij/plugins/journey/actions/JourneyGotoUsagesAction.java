package org.jetbrains.intellij.plugins.journey.actions;

import com.intellij.find.FindBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.plugins.journey.JourneyDataKeys;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramDataModel;

import java.util.ArrayList;
import java.util.List;

public class JourneyGotoUsagesAction extends AnAction implements JourneyEditorOverrideActionPromoter {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) return;
    JourneyDiagramDataModel diagramDataModel = editor.getUserData(JourneyDataKeys.JOURNEY_DIAGRAM_DATA_MODEL);
    if (diagramDataModel == null) return;
    int offset = editor.getCaretModel().getOffset();
    Project project = e.getProject();
    if (project == null) return;
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) return;
    PsiElement psiElementAtCaret = psiFile.findElementAt(offset);
    if (psiElementAtCaret == null) return;
    PsiMember finalPsiMember = PsiTreeUtil.getParentOfType(psiElementAtCaret, PsiMember.class);
    if (finalPsiMember == null) return;

    final List<PsiElement> result = new ArrayList<>();
    PsiMember psiMember = finalPsiMember;
    if (finalPsiMember instanceof PsiMethod psiMethod) {
      while (psiMethod.findSuperMethods(true).length > 0) {
        psiMethod = psiMethod.findSuperMethods()[0];
      }
      psiMember = psiMethod;
    }

    ReferencesSearch.search(psiMember).forEach(reference -> {
      PsiElement element = reference.getElement();
      var isImport = ReadAction.compute(() -> {
        PsiImportList importBlock = PsiTreeUtil.getParentOfType(element, PsiImportList.class);
        return importBlock != null;
      });
      if (!isImport && element instanceof PsiReferenceExpression) {
        result.add(element);
      }
    });

    JourneyGoToActionUtil.navigateWithPopup(
      editor,
      FindBundle.message("show.usages.ambiguous.title"),
      result,
      JourneyGoToActionUtil::getPresentationOfClosestMember,
      (e2) -> {
        diagramDataModel.addEdgeAsync(e2, finalPsiMember);
      });
  }
}

