/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.ui;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created with IntelliJ IDEA.
 * User: zajac
 * Date: 4/4/12
 * Time: 5:51 PM
 * To change this template use File | Settings | File Templates.
 */
public class BreakpointEditor {
  public JPanel getMainPanel() {
    return myMainPanel;
  }

  private void createUIComponents() {
    AnAction action = ActionManager.getInstance().getAction(XDebuggerActions.VIEW_BREAKPOINTS);
    String shortcutText = action != null ? KeymapUtil.getFirstKeyboardShortcutText(action) : null;
    String text = shortcutText != null ? "More (" + shortcutText + ")" : "More";
    myShowMoreOptionsLink = new LinkLabel(text, null, new LinkListener() {
      @Override
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        if (myDelegate != null) {
          myDelegate.more();
        }
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
  private LinkLabel myShowMoreOptionsLink;
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
      public void update(AnActionEvent e) {
        super.update(e);
        Project project = getEventProject(e);
        boolean lookup = project != null && LookupManager.getInstance(project).getActiveLookup() != null;
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        final Component owner = IdeFocusManager.findInstance().getFocusOwner();
        if (owner != null) {
          final JComboBox comboBox = UIUtil.getParentOfType(JComboBox.class, owner);
          if (comboBox != null && comboBox.isPopupVisible()) {
            lookup = true;
          }
        }
        e.getPresentation().setEnabled(!lookup && (editor == null || StringUtil.isEmpty(editor.getSelectionModel().getSelectedText())) );
      }

      public void actionPerformed(AnActionEvent e) {
        done();
      }
    };
    doneAction.registerCustomShortcutSet(new CompositeShortcutSet(CommonShortcuts.ESCAPE,
                                                                  CommonShortcuts.ENTER,
                                                                  CommonShortcuts.CTRL_ENTER), myMainPanel);
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
