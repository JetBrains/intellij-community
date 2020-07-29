// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.encoding;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.GlobalUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;

public class ChangeFileEncodingAction extends AnAction implements DumbAware, LightEditCompatible {
  private final boolean allowDirectories;

  public ChangeFileEncodingAction() {
    this(false);
  }

  public ChangeFileEncodingAction(boolean allowDirectories) {
    this.allowDirectories = allowDirectories;
  }

  private boolean checkEnabled(@NotNull VirtualFile virtualFile) {
    if (allowDirectories && virtualFile.isDirectory()) return true;
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    Document document = documentManager.getDocument(virtualFile);
    if (document == null) return false;

    return EncodingUtil.checkCanConvert(virtualFile) == null || EncodingUtil.checkCanReload(virtualFile, null) == null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VirtualFile myFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    boolean enabled = myFile != null && checkEnabled(myFile);
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(myFile != null);
  }

  @Override
  public final void actionPerformed(@NotNull final AnActionEvent e) {
    DataContext dataContext = e.getDataContext();

    ListPopup popup = createPopup(dataContext);
    if (popup != null) {
      popup.showInBestPositionFor(dataContext);
    }
  }

  @Nullable
  public ListPopup createPopup(@NotNull DataContext dataContext) {
    final VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (virtualFile == null) return null;
    boolean enabled = checkEnabled(virtualFile);
    if (!enabled) return null;
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    final Document document = documentManager.getDocument(virtualFile);
    if (!allowDirectories && virtualFile.isDirectory() || document == null && !virtualFile.isDirectory()) return null;

    final byte[] bytes;
    try {
      bytes = virtualFile.isDirectory() ? null : VfsUtilCore.loadBytes(virtualFile);
    }
    catch (IOException e) {
      return null;
    }
    DefaultActionGroup group = createActionGroup(virtualFile, editor, document, bytes, null);

    return JBPopupFactory.getInstance().createActionGroupPopup(getTemplatePresentation().getText(),
      group, dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
  }

  @NotNull
  public DefaultActionGroup createActionGroup(@Nullable VirtualFile myFile,
                                              Editor editor,
                                              Document document,
                                              byte[] bytes,
                                              @Nullable String clearItemText) {
    return new ChooseFileEncodingAction(myFile) {
     @Override
     public void update(@NotNull final AnActionEvent e) {
     }

     @NotNull
     @Override
     protected DefaultActionGroup createPopupActionGroup(JComponent button) {
       return createCharsetsActionGroup(clearItemText, null, charset -> "Change encoding to '" + charset.displayName() + "'");
       // no 'clear'
     }

      @Override
      protected void chosen(@Nullable VirtualFile virtualFile, @NotNull Charset charset) {
        ChangeFileEncodingAction.this.chosen(document, editor, virtualFile, bytes, charset);
      }
   }
   .createPopupActionGroup(null);
  }

  // returns true if charset was changed, false if failed
  protected boolean chosen(Document document,
                           Editor editor,
                           @Nullable VirtualFile virtualFile,
                           byte[] bytes,
                           @NotNull Charset charset) {
    if (virtualFile == null) {
      return false;
    }

    String text = document.getText();
    EncodingUtil.Magic8 isSafeToConvert = EncodingUtil.isSafeToConvertTo(virtualFile, text, bytes, charset);
    EncodingUtil.Magic8 isSafeToReload = EncodingUtil.isSafeToReloadIn(virtualFile, text, bytes, charset);

    Project project = editor == null ? null : editor.getProject();
    if (project == null) {
      project = ProjectLocator.getInstance().guessProjectForFile(virtualFile);
    }
    return changeTo(Objects.requireNonNull(project), document, editor, virtualFile, charset, isSafeToConvert, isSafeToReload);
  }

  public static boolean changeTo(@NotNull Project project,
                                 @NotNull Document document,
                                 Editor editor,
                                 @NotNull VirtualFile virtualFile,
                                 @NotNull Charset charset,
                                 @NotNull EncodingUtil.Magic8 isSafeToConvert, @NotNull EncodingUtil.Magic8 isSafeToReload) {
    Charset oldCharset = virtualFile.getCharset();
    Runnable undo;
    Runnable redo;
    if (isSafeToConvert == EncodingUtil.Magic8.ABSOLUTELY && isSafeToReload == EncodingUtil.Magic8.ABSOLUTELY) {
      final EncodingManager encodingManager = EncodingProjectManager.getInstance(project);
      //change and forget
      undo = () -> encodingManager.setEncoding(virtualFile, oldCharset);
      redo = () -> encodingManager.setEncoding(virtualFile, charset);
    }
    else {
      IncompatibleEncodingDialog dialog = new IncompatibleEncodingDialog(virtualFile, charset, isSafeToReload, isSafeToConvert);
      dialog.show();
      if (dialog.getExitCode() == IncompatibleEncodingDialog.RELOAD_EXIT_CODE) {
        undo = () -> EncodingUtil.reloadIn(virtualFile, oldCharset, project);
        redo = () -> EncodingUtil.reloadIn(virtualFile, charset, project);
      }
      else if (dialog.getExitCode() == IncompatibleEncodingDialog.CONVERT_EXIT_CODE) {
        undo = () -> EncodingUtil.saveIn(project, document, editor, virtualFile, oldCharset);
        redo = () -> EncodingUtil.saveIn(project, document, editor, virtualFile, charset);
      }
      else {
        return false;
      }
    }

    final UndoableAction action = new GlobalUndoableAction(virtualFile) {
      @Override
      public void undo() {
        // invoke later because changing document inside undo/redo is not allowed
        Application application = ApplicationManager.getApplication();
        application.invokeLater(undo, ModalityState.NON_MODAL, project.getDisposed());
      }

      @Override
      public void redo() {
        // invoke later because changing document inside undo/redo is not allowed
        Application application = ApplicationManager.getApplication();
        application.invokeLater(redo, ModalityState.NON_MODAL, project.getDisposed());
      }
    };

    redo.run();
    CommandProcessor.getInstance().executeCommand(project, () -> {
      UndoManager undoManager = UndoManager.getInstance(project);
      undoManager.undoableActionPerformed(action);
    }, IdeBundle.message("command.change.encoding.for.0", virtualFile.getName()), null, UndoConfirmationPolicy.REQUEST_CONFIRMATION);

    return true;
  }
}
