package org.jetbrains.intellij.plugins.journey.actions;

import com.intellij.codeInsight.navigation.UtilKt;
import com.intellij.find.FindBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.plugins.journey.JourneyDataKeys;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramDataModel;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.ui.list.TargetPopup.createTargetPopup;

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
    PsiMember psiMember = PsiTreeUtil.getParentOfType(psiElementAtCaret, PsiMember.class);
    if (psiMember == null) return;

    final List<PsiElement> result = new ArrayList<>();
    ReferencesSearch.search(psiMember).forEach(reference -> {
      PsiElement resolve = reference.getElement();
      result.add(resolve);
    });

    if (result.isEmpty()) {
      return;
    }

    if (result.size() > 1) {
      List<PsiElement> filteredResult = result.stream().filter(element -> element instanceof PsiReferenceExpressionImpl).toList();
      // TODO replace createTargetPopup to getPsiElementPopup
      createTargetPopup(FindBundle.message("show.usages.ambiguous.title"), filteredResult, (e1) -> {
        PsiElement element = e1.getNavigationElement();
        var member = PsiUtil.tryFindParentOrNull(element, it -> it instanceof PsiMember);
        if (member != null) {
          element = member;
        }
        return UtilKt.targetPresentation(element);
      }, (e1) -> {
        diagramDataModel.addEdge(e1, psiMember);
      }).showInBestPositionFor(editor);
    } else {
      diagramDataModel.addEdge(result.get(0), psiMember);
    }
  }

}
