package com.intellij.byteCodeViewer;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.compiler.impl.TranslatingCompilerFilesMonitor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.popup.NotLookupOrSearchCondition;
import com.intellij.ui.popup.PopupPositionManager;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: 5/4/12
 */
public class ShowByteCodeAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(false);
    e.getPresentation().setIcon(AllIcons.Toolwindows.Documentation);
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project != null) {
      final PsiElement psiElement = getPsiElement(e.getDataContext(), project, e.getData(PlatformDataKeys.EDITOR));
      if (psiElement != null) {
        if (psiElement.getContainingFile() instanceof PsiClassOwner && PsiTreeUtil.getParentOfType(psiElement, PsiClass.class, false) != null) {
          e.getPresentation().setEnabled(true);
        }
      }
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);

    final PsiElement psiElement = getPsiElement(dataContext, project, editor);
    if (psiElement == null) return;

    final String psiElementTitle = ByteCodeViewerManager.getInstance(project).getTitle(psiElement);

    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(psiElement);
    if (virtualFile == null) return;

    if (ProjectRootManager.getInstance(project).getFileIndex().isInContent(virtualFile) && TranslatingCompilerFilesMonitor.getInstance().isMarkedForCompilation(project, virtualFile)) {
      Messages.showWarningDialog(project, "Unable to show byte code for '" + psiElementTitle + "'. Class file does not exist or is out-of-date.", "Class File Out-Of-Date");
      return;
    }

    final ByteCodeViewerManager codeViewerManager = ByteCodeViewerManager.getInstance(project);
    if (codeViewerManager.hasActiveDockedDocWindow()) {
      codeViewerManager.doUpdateComponent(psiElement);
    } else {
      final String byteCode = ByteCodeViewerManager.getByteCode(psiElement);
      if (byteCode == null) {
        Messages.showErrorDialog(project, "Unable to parse class file for '" + psiElementTitle + "'.", "Byte Code not Found");
        return;
      }
      final ByteCodeViewerComponent component = new ByteCodeViewerComponent(project, null);
      component.setText(byteCode, psiElement);
      Processor<JBPopup> pinCallback = new Processor<JBPopup>() {
        @Override
        public boolean process(JBPopup popup) {

          codeViewerManager.createToolWindow(psiElement, psiElement);
          popup.cancel();
          return false;
        }
      };

      final JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(component, null)
        .setRequestFocusCondition(project, NotLookupOrSearchCondition.INSTANCE)
        .setProject(project)
        .setDimensionServiceKey(project, DocumentationManager.JAVADOC_LOCATION_AND_SIZE, false)
        .setResizable(true)
        .setMovable(true)
        .setRequestFocus(LookupManager.getActiveLookup(editor) == null)
        .setTitle(psiElementTitle + " Bytecode")
        .setCouldPin(pinCallback)
        .createPopup();
      Disposer.register(popup, component);


      PopupPositionManager.positionPopupInBestPosition(popup, editor, dataContext);
    }
  }

  @Nullable
  private static PsiElement getPsiElement(DataContext dataContext, Project project, Editor editor) {
    PsiElement psiElement = null;
    if (editor == null) {
      psiElement = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    } else {
      final PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
      final Editor injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file);
      if (injectedEditor != null) {
        PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(injectedEditor, project);
        psiElement = psiFile != null ? psiFile.findElementAt(injectedEditor.getCaretModel().getOffset()) : null;
      }

      if (file != null && psiElement == null) {
        psiElement = file.findElementAt(editor.getCaretModel().getOffset());
      }
    }

    return psiElement;
  }
}
