// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.components.ActionLink;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@ApiStatus.Internal
public class BreakpointEditor {
  public JPanel getMainPanel() {
    return myMainPanel;
  }

  private void createUIComponents() {
    AnAction action = ActionManager.getInstance().getAction(XDebuggerActions.VIEW_BREAKPOINTS);
    String shortcutText = action != null ? KeymapUtil.getFirstKeyboardShortcutText(action) : null;
    String text = XDebuggerBundle.message("xbreakpoints.popup.more.label");
    if (shortcutText != null) {
      text += " (" + shortcutText + ")";
    }
    myShowMoreOptionsLink = new ActionLink(text, e -> {
      if (myDelegate != null) {
        myDelegate.more();
      }
    });
  }

  public void setShowMoreOptionsLink(boolean b) {
    myShowMoreOptionsLink.setVisible(b);
  }

  public interface Delegate {
    void done();

    void more();
  }

  private JPanel myMainPanel;
  private JButton myDoneButton;
  private JPanel myPropertiesPlaceholder;
  private ActionLink myShowMoreOptionsLink;
  private Delegate myDelegate;

  public BreakpointEditor() {
    myDoneButton.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        done();
      }
    });

    final AnAction doneAction = new DumbAwareAction() {
      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        Project project = getEventProject(e);
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        boolean disabled = project != null &&
                         (LookupManager.getInstance(project).getActiveLookup() != null ||
                          (editor != null && TemplateManager.getInstance(project).getActiveTemplate(editor) != null));
        final Component owner = IdeFocusManager.findInstance().getFocusOwner();
        if (owner != null) {
          final JComboBox comboBox = ComponentUtil.getParentOfType((Class<? extends JComboBox>)JComboBox.class, owner);
          if (comboBox != null && comboBox.isPopupVisible()) {
            disabled = true;
          }
        }
        e.getPresentation().setEnabled(!disabled && (editor == null || StringUtil.isEmpty(editor.getSelectionModel().getSelectedText())) );
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        done();
      }
    };
    doneAction.registerCustomShortcutSet(new CompositeShortcutSet(CommonShortcuts.ESCAPE,
                                                                  CommonShortcuts.ENTER,
                                                                  CommonShortcuts.getCtrlEnter()), myMainPanel);
    myMainPanel.setFocusCycleRoot(true);
    myMainPanel.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
  }

  private void done() {
    if (myDelegate != null) {
      myDelegate.done();
    }
  }

  public void setPropertiesPanel(JComponent p) {
    myPropertiesPlaceholder.removeAll();
    myPropertiesPlaceholder.add(p, BorderLayout.CENTER);
  }

  public void setDelegate(Delegate d) {
    myDelegate = d;
  }
}
