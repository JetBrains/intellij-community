// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Processor;
import com.intellij.util.ui.JBEmptyBorder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

final class ShowByteCodeAction extends AnAction implements UpdateInBackground {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(false);
    e.getPresentation().setIcon(AllIcons.Actions.Preview);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      final PsiElement psiElement = getPsiElement(e.getDataContext(), project, e.getData(CommonDataKeys.EDITOR));
      if (psiElement != null) {
        if (psiElement.getContainingFile() instanceof PsiClassOwner) {
          e.getPresentation().setEnabled(true);
        }
      }
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = e.getProject();
    if (project == null) return;
    final Editor editor = e.getData(CommonDataKeys.EDITOR);

    final PsiElement psiElement = getPsiElement(dataContext, project, editor);
    if (psiElement == null) return;

    if (ByteCodeViewerManager.getContainingClass(psiElement) == null) {
      Messages.showWarningDialog(project, JavaByteCodeViewerBundle.message("bytecode.class.in.selection.message"),
                                 JavaByteCodeViewerBundle.message("bytecode.not.found.message"));
      return;
    }

    final String psiElementTitle = ByteCodeViewerManager.getInstance(project).getTitle(psiElement);

    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(psiElement);
    if (virtualFile == null) return;

    final RelativePoint bestPopupLocation = JBPopupFactory.getInstance().guessBestPopupLocation(dataContext);

    final SmartPsiElementPointer element = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psiElement);
    ProgressManager.getInstance().run(new Task.Backgroundable(project, JavaByteCodeViewerBundle.message("looking.for.bytecode.progress")) {
      private String myByteCode;
      private @Nls String myErrorTitle;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        if (ProjectRootManager.getInstance(project).getFileIndex().isInContent(virtualFile) &&
            isMarkedForCompilation(project, virtualFile)) {
          myErrorTitle = JavaByteCodeViewerBundle.message("class.file.may.be.out.of.date");
        }
        myByteCode = ReadAction.compute(() -> {
          PsiElement targetElement = element.getElement();
          return targetElement != null ? ByteCodeViewerManager.getByteCode(targetElement) : null;
        });
      }

      @Override
      public void onSuccess() {
        if (project.isDisposed()) return;

        final PsiElement targetElement = element.getElement();
        if (targetElement == null) return;

        final ByteCodeViewerManager codeViewerManager = ByteCodeViewerManager.getInstance(project);
        if (codeViewerManager.hasActiveDockedDocWindow()) {
          codeViewerManager.doUpdateComponent(targetElement, myByteCode);
        }
        else {
          if (myByteCode == null) {
            Messages.showErrorDialog(project, JavaByteCodeViewerBundle.message("bytecode.parser.failure.message", psiElementTitle),
                                     JavaByteCodeViewerBundle.message("bytecode.not.found.title"));
            return;
          }

          final ByteCodeViewerComponent component = new ByteCodeViewerComponent(project);
          component.setText(myByteCode, targetElement);
          Processor<JBPopup> pinCallback = popup -> {
            codeViewerManager.recreateToolWindow(targetElement, targetElement);
            popup.cancel();
            return false;
          };

          if (myErrorTitle != null) {
            JLabel errorLabel = new JLabel(myErrorTitle);
            Color color = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.NOTIFICATION_BACKGROUND);
            if (color != null) {
              errorLabel.setBorder(new JBEmptyBorder(2));
              errorLabel.setBackground(color);
              errorLabel.setOpaque(true);
            }
            component.add(errorLabel, BorderLayout.NORTH);
          }

          final JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(component, component.getEditorComponent())
            .setProject(project)
            .setDimensionServiceKey(project, ShowByteCodeAction.class.getName(), false)
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(LookupManager.getActiveLookup(editor) == null)
            .setTitle(JavaByteCodeViewerBundle.message("popup.title.element.bytecode", psiElementTitle))
            .setCouldPin(pinCallback)
            .createPopup();
          Disposer.register(popup, component);

          if (editor != null) {
            popup.showInBestPositionFor(editor);
          } else {
            popup.show(bestPopupLocation);
          }
        }
      }
    });
  }

  private static boolean isMarkedForCompilation(Project project, VirtualFile virtualFile) {
    final CompilerManager compilerManager = CompilerManager.getInstance(project);
    return !compilerManager.isUpToDate(compilerManager.createFilesCompileScope(new VirtualFile[]{virtualFile}));
  }

  @Nullable
  private static PsiElement getPsiElement(DataContext dataContext, Project project, @Nullable Editor editor) {
    PsiElement psiElement;
    if (editor == null) {
      psiElement = dataContext.getData(CommonDataKeys.PSI_ELEMENT);
    }
    else {
      final PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
      final Editor injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file);
      psiElement = findElementInFile(PsiUtilBase.getPsiFileInEditor(injectedEditor, project), injectedEditor);

      if (file != null && psiElement == null) {
        psiElement = findElementInFile(file, editor);
      }
    }

    return psiElement;
  }

  private static PsiElement findElementInFile(@Nullable PsiFile psiFile, @NotNull Editor editor) {
    return psiFile != null ? psiFile.findElementAt(editor.getCaretModel().getOffset()) : null;
  }
}