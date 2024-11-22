package org.jetbrains.intellij.plugins.journey.actions;

import com.intellij.codeInsight.navigation.UtilKt;
import com.intellij.find.FindBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.plugins.journey.JourneyDataKeys;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramDataModel;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

import java.util.Arrays;
import java.util.List;

import static com.intellij.ui.list.TargetPopup.createTargetPopup;

/**
 * Finds super class\method and instead of navigating to them, adds them on the journey.
 * @see com.intellij.codeInsight.navigation.actions.GotoSuperAction
 * @see com.intellij.codeInsight.navigation.JavaGotoSuperHandler
 */
public class JourneyGotoSuperAction extends AnAction implements JourneyEditorOverrideActionPromoter {

  private static final Logger LOG = Logger.getInstance(JourneyGotoSuperAction.class);

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

    List<PsiElement> result = Arrays.asList(findSuperElements(psiFile, offset));

    if (result.isEmpty()) {
      LOG.info("No super elements found");
    }
    if (result.size() == 1) {
      diagramDataModel.addEdge(result.get(0), psiMember);
    }
    if (result.size() > 1) {
      createTargetPopup(FindBundle.message("show.usages.ambiguous.title"), result, (e1) -> {
        PsiElement element = e1.getNavigationElement();
        var member = PsiUtil.tryFindParentOrNull(element, it -> it instanceof PsiMember);
        if (member != null) {
          element = member;
        }
        return UtilKt.targetPresentation(element);
      }, (e1) -> {
        diagramDataModel.addEdge(e1, psiMember);
      }).showInBestPositionFor(editor);
    }
  }

  /**
   * Copy of {@link com.intellij.codeInsight.navigation.JavaGotoSuperHandler#findSuperElements(PsiFile, int)}
   */
  @SuppressWarnings("DuplicatedCode")
  private static PsiElement @NotNull [] findSuperElements(@NotNull PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    if (element == null) return PsiElement.EMPTY_ARRAY;

    final PsiElement psiElement = PsiTreeUtil.getParentOfType(element, PsiFunctionalExpression.class, PsiMember.class);
    if (psiElement instanceof PsiFunctionalExpression) {
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(psiElement);
      if (interfaceMethod != null) {
        return ArrayUtil.prepend(interfaceMethod, interfaceMethod.findSuperMethods(false));
      }
    }

    final PsiNameIdentifierOwner parent = PsiTreeUtil.getNonStrictParentOfType(element, PsiMethod.class, PsiClass.class);
    if (parent == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    return FindSuperElementsHelper.findSuperElements(parent);
  }
}
