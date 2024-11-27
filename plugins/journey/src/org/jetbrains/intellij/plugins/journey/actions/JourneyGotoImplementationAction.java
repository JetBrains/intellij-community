package org.jetbrains.intellij.plugins.journey.actions;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.navigation.ImplementationSearcher;
import com.intellij.find.FindBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.JourneyDataKeys;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramDataModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.codeInsight.navigation.GotoImplementationHandler.tryGetNavigationSourceOffsetFromGutterIcon;
import static com.intellij.ui.list.TargetPopup.createTargetPopup;

/**
 * Finds subclass \ override method and instead of navigating to them, adds them on the journey.
 * @see com.intellij.codeInsight.navigation.actions.GotoImplementationAction
 */
public class JourneyGotoImplementationAction extends AnAction implements JourneyEditorOverrideActionPromoter {

  private static final Logger LOG = Logger.getInstance(JourneyGotoImplementationAction.class);

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


    PsiElement source = TargetElementUtil.getInstance().findTargetElement(editor, ImplementationSearcher.getFlags(), offset);
    if (source == null) {
      offset = tryGetNavigationSourceOffsetFromGutterIcon(editor, IdeActions.ACTION_GOTO_IMPLEMENTATION);
      if (offset >= 0) {
        source = TargetElementUtil.getInstance().findTargetElement(editor, ImplementationSearcher.getFlags(), offset);
      }
    }
    if (source == null) {
      LOG.warn("No source found");
      return;
    }
    //PsiElement[] targets = findTargets(editor, offset, source);
    PsiElement[] targets = targetElements(editor, offset);
    if (targets == null) {
      LOG.warn("No targets found");
      return;
    }
    List<PsiElement> result = Arrays.asList(targets);

    JourneyGoToActionUtil.navigateWithPopup(
      editor,
      FindBundle.message("show.usages.ambiguous.title"),
      result,
      JourneyGoToActionUtil::getPresentationOfClosestMember,
      (e1) -> {
        diagramDataModel.addEdgeAsync(e1, psiMember);
      }
    );
  }

  /**
   * Copy of the original private method in codeinsight.
   * @see com.intellij.codeInsight.navigation.actions.GotoImplementationAction#targetElements(Editor, int)
   */
  @SuppressWarnings("DuplicatedCode")
  private static @NotNull PsiElement @Nullable [] targetElements(@NotNull Editor editor, int offset) {
    final PsiElement element = TargetElementUtil.getInstance().findTargetElement(editor, ImplementationSearcher.getFlags(), offset);
    return new ImplementationSearcher() {
      @Override
      protected PsiElement @NotNull [] searchDefinitions(final PsiElement element, Editor editor) {
        final List<PsiElement> found = new ArrayList<>(2);
        DefinitionsScopedSearch.search(element, getSearchScope(element, editor)).forEach(psiElement -> {
          found.add(psiElement);
          return found.size() != 2;
        });
        return PsiUtilCore.toPsiElementArray(found);
      }
    }.searchImplementations(editor, element, offset);
  }

}
