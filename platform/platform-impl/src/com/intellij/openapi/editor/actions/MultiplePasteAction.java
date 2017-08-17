/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.actions;

import com.intellij.ide.CopyPasteManagerEx;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OptionAction;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

/**
 * @author max
 */
public class MultiplePasteAction extends AnAction implements DumbAware {
  private static final int PASTE_SIMPLE_EXIT_CODE = DialogWrapper.NEXT_USER_EXIT_CODE;

  public MultiplePasteAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    Component focusedComponent = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);

    if (!(focusedComponent instanceof JComponent)) return;

    final ContentChooser<Transferable> chooser = new ClipboardContentChooser(project);

    if (!chooser.getAllContents().isEmpty()) {
      chooser.show();
    }
    else {
      chooser.close(DialogWrapper.CANCEL_EXIT_CODE);
    }

    if (chooser.getExitCode() == DialogWrapper.OK_EXIT_CODE || chooser.getExitCode() == PASTE_SIMPLE_EXIT_CODE) {
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

        final AnAction pasteAction = ActionManager.getInstance().getAction(chooser.getExitCode() == PASTE_SIMPLE_EXIT_CODE 
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
  public void update(AnActionEvent e) {
    final boolean enabled = isEnabled(e);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(enabled);
    }
    else {
      e.getPresentation().setEnabled(enabled);
    }
  }

  private static boolean isEnabled(AnActionEvent e) {
    Object component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    if (!(component instanceof JComponent)) return false;
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor != null) return !editor.isViewer();
    Action pasteAction = ((JComponent)component).getActionMap().get(DefaultEditorKit.pasteAction);
    return pasteAction != null;
  }

  private static class ClipboardContentChooser extends ContentChooser<Transferable> {

    public ClipboardContentChooser(Project project) {
      super(project, UIBundle.message("choose.content.to.paste.dialog.title"), true, true);
      setOKButtonText(UIBundle.message("choose.content.to.paste.dialog.ok.button"));
    }

    @Nullable
    @Override
    protected String getHelpId() {
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

    @NotNull
    @Override
    protected List<Transferable> getContents() {
      return Arrays.asList(CopyPasteManager.getInstance().getAllContents());
    }

    @Override
    protected void removeContentAt(final Transferable content) {
      CopyPasteManagerEx.getInstanceEx().removeContent(content);
    }

    @Override
    protected void createDefaultActions() {
      super.createDefaultActions();
      myOKAction = new PasteAction();
    }

    class PasteAction extends OkAction implements OptionAction {
      private final Action[] myActions = new Action[] {new PasteSimpleAction()};
        
      @NotNull
      @Override
      public Action[] getOptions() {
        return myActions;
      }
    }

    class PasteSimpleAction extends DialogWrapperAction {
      private PasteSimpleAction() {
        super(UIBundle.message("choose.content.to.paste.dialog.simple.button"));
      }

      @Override
      protected void doAction(ActionEvent e) {
        close(PASTE_SIMPLE_EXIT_CODE);
      }
    }
  }
}
