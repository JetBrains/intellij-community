package org.jetbrains.intellij.plugins.journey.actions;

import com.intellij.codeInsight.navigation.impl.NavigationActionResult;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.navigation.NavigationRequest;
import com.intellij.platform.backend.navigation.impl.SourceNavigationRequest;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.plugins.journey.JourneyDataKeys;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramDataModel;

public class JourneyGoToDeclarationOrUsagesAction extends AnAction implements JourneyEditorOverrideActionPromoter {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) return;
    JourneyDiagramDataModel diagramDataModel = editor.getUserData(JourneyDataKeys.JOURNEY_DIAGRAM_DATA_MODEL);
    if (diagramDataModel == null) return;
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
    if (virtualFile == null) return;
    int offset = editor.getCaretModel().getOffset();
    Project project = e.getProject();
    if (project == null) return;
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) return;
    PsiElement psiElementAtCaret = psiFile.findElementAt(offset);
    if (psiElementAtCaret == null) return;
    PsiMember psiMember = PsiTreeUtil.getParentOfType(psiElementAtCaret, PsiMember.class);
    if (psiMember == null) return;

    NavigationActionResult declarations1 = JourneyGotoDeclarationOnlyHandler.Companion.findDeclarations(
      editor, diagramDataModel, project, psiFile
    );

    var shouldGoUsages = false;
    if (declarations1 != null) {
      if (declarations1 instanceof NavigationActionResult.SingleTarget singleTarget) {
        NavigationRequest request = ActionUtil.underModalProgress(project, "Resolving declaration", () ->
          singleTarget.getRequestor().navigationRequest()
        );
        if (request instanceof SourceNavigationRequest sourceNavigationRequest) {
          /*
           TODO
           Currently, navigating to a declaration results in staying on the same file if
           the caret is already at the declaration. This prevents navigating to usages.
           As a temporary solution, disable "go to declaration" if already inside the target element.
           In the future, implement a method to accurately check if the caret is already at the declaration.
          */
          if (sourceNavigationRequest.getFile().equals(virtualFile)) {
            RangeMarker marker = sourceNavigationRequest.getElementRangeMarker();
            if (marker != null && marker.getTextRange().contains(offset)) {
              shouldGoUsages = true;
            }
          }
        }
      }
      if (!shouldGoUsages) {
        JourneyGotoDeclarationOnlyHandler.Companion.gotoDeclaration(project, editor, declarations1, diagramDataModel);
        return;
      }
    }

    new JourneyGotoUsagesAction().actionPerformed(e);
  }
}
