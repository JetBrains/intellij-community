// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.ide.CopyPasteManagerEx;
import com.intellij.ide.DataManager;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.DefaultEditorKit;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public final class MultiplePasteAction extends AnAction implements DumbAware {

  private static final char P = 'P';

  public MultiplePasteAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    Component focusedComponent = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);

    if (!(focusedComponent instanceof JComponent)) return;

    final ContentChooser<Transferable> chooser = new ClipboardContentChooser(project);

    if (!chooser.getAllContents().isEmpty()) {
      chooser.show();
    }
    else {
      chooser.close(DialogWrapper.CANCEL_EXIT_CODE);
    }

    if (chooser.getExitCode() == DialogWrapper.OK_EXIT_CODE || chooser.getExitCode() == getPasteSimpleExitCode()) {
      List<Transferable> selectedContents = chooser.getSelectedContents();
      CopyPasteManagerEx copyPasteManager = CopyPasteManagerEx.getInstanceEx();
      if (selectedContents.size() == 1) {
        copyPasteManager.moveContentToStackTop(selectedContents.get(0));
      }
      else {
        copyPasteManager.setContents(new StringSelection(chooser.getSelectedText()));
      }

      if (editor != null) {
        if (editor.isViewer()) return;

        final AnAction pasteAction = ActionManager.getInstance().getAction(chooser.getExitCode() == getPasteSimpleExitCode()
                                                                           ? IdeActions.ACTION_EDITOR_PASTE_SIMPLE
                                                                           : IdeActions.ACTION_PASTE);
        AnActionEvent newEvent = new AnActionEvent(e.getInputEvent(),
                                                   DataManager.getInstance().getDataContext(focusedComponent),
                                                   e.getPlace(), e.getPresentation(),
                                                   ActionManager.getInstance(),
                                                   e.getModifiers());
        pasteAction.actionPerformed(newEvent);
      }
      else {
        final Action pasteAction = ((JComponent)focusedComponent).getActionMap().get(DefaultEditorKit.pasteAction);
        if (pasteAction != null) {
          pasteAction.actionPerformed(new ActionEvent(focusedComponent, ActionEvent.ACTION_PERFORMED, ""));
        }
      }
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final boolean enabled = isEnabled(e);
    e.getPresentation().setEnabled(enabled);
    if (e.isFromContextMenu()) {
      e.getPresentation().setVisible(enabled);
    }
  }

  private static boolean isEnabled(@NotNull AnActionEvent e) {
    Object component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    if (!(component instanceof JComponent)) return false;
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor != null) return !editor.isViewer();
    Action pasteAction = ((JComponent)component).getActionMap().get(DefaultEditorKit.pasteAction);
    return pasteAction != null;
  }

  private static int getPasteSimpleExitCode() {
    return DialogWrapper.NEXT_USER_EXIT_CODE;
  }

  private static final class ClipboardContentChooser extends ContentChooser<Transferable> {

    ClipboardContentChooser(Project project) {
      super(project, UIBundle.message("choose.content.to.paste.dialog.title"), true, true);
      setOKButtonText(ActionsBundle.actionText(IdeActions.ACTION_EDITOR_PASTE));
      setOKButtonMnemonic(P);
      setKeepPopupsOpen(true);
    }

    @Override
    protected Action @NotNull [] createActions() {
      return new Action[]{getHelpAction(), getOKAction(), new PasteSimpleAction(), getCancelAction()};
    }

    @Override
    protected @NotNull String getHelpId() {
      return "ixPasteSelected";
    }


    @Override
    protected String getStringRepresentationFor(final Transferable content) {
      try {
        return (String)content.getTransferData(DataFlavor.stringFlavor);
      }
      catch (UnsupportedFlavorException | IOException e1) {
        return "";
      }
    }

    @Override
    protected @NotNull List<Transferable> getContents() {
      return Arrays.asList(CopyPasteManager.getInstance().getAllContents());
    }

    @Override
    protected void removeContentAt(final Transferable content) {
      CopyPasteManagerEx.getInstanceEx().removeContent(content);
    }

    final class PasteSimpleAction extends DialogWrapperAction {
      private PasteSimpleAction() {
        super(ActionsBundle.actionText(IdeActions.ACTION_EDITOR_PASTE_SIMPLE));
      }

      @Override
      protected void doAction(ActionEvent e) {
        close(getPasteSimpleExitCode());
      }
    }
  }
}