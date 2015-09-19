/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.util;

import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.PatternUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GotoLineNumberDialog extends DialogWrapper {
  private JTextField myField;
  private JTextField myOffsetField;
  private final Editor myEditor;
  private final Pattern myPattern = PatternUtil.compileSafe("\\s*(\\d+)\\s*(?:[,:]?\\s*(\\d+)?)?\\s*", null);

  public GotoLineNumberDialog(Project project, Editor editor) {
    super(project, true);
    myEditor = editor;
    setTitle("Go to Line");
    init();
  }

  protected void doOKAction() {
    LogicalPosition position = getLogicalPosition();
    if (position == null) return;

    myEditor.getCaretModel().removeSecondaryCarets();
    myEditor.getCaretModel().moveToLogicalPosition(position);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    myEditor.getSelectionModel().removeSelection();
    IdeFocusManager.getGlobalInstance().requestFocus(myEditor.getContentComponent(), true);
    super.doOKAction();
  }

  @Nullable
  private LogicalPosition getLogicalPosition() {
    Matcher m = myPattern.matcher(getText());
    if (!m.matches()) return null;

    int l = StringUtil.parseInt(m.group(1), -1);
    int c = StringUtil.parseInt(m.group(2), -1);
    return l > 0 ? new LogicalPosition(l - 1, Math.max(0, c - 1)) : null;
  }

  private static boolean isInternal() {
    return ApplicationManagerEx.getApplicationEx().isInternal();
  }

  public JComponent getPreferredFocusedComponent() {
    return myField;
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  private String getText() {
    return myField.getText();
  }

  protected JComponent createNorthPanel() {
    class MyTextField extends JTextField {
      public MyTextField() {
        super("");
      }

      public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(200, d.height);
      }
    }

    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(4, 0, 8, 8);
    gbConstraints.fill = GridBagConstraints.VERTICAL;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.anchor = GridBagConstraints.EAST;
    JLabel label = new JLabel("Line [:column]:");
    panel.add(label, gbConstraints);

    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    myField = new MyTextField();
    panel.add(myField, gbConstraints);
    LogicalPosition position = myEditor.getCaretModel().getLogicalPosition();
    myField.setText(String.format("%d:%d", position.line + 1, position.column + 1));

    if (isInternal()) {
      gbConstraints.gridy = 1;
      gbConstraints.weightx = 0;
      gbConstraints.weighty = 1;
      gbConstraints.anchor = GridBagConstraints.EAST;
      final JLabel offsetLabel = new JLabel("Offset:");
      panel.add(offsetLabel, gbConstraints);

      gbConstraints.fill = GridBagConstraints.BOTH;
      gbConstraints.weightx = 1;
      myOffsetField = new MyTextField();
      panel.add(myOffsetField, gbConstraints);
      myOffsetField.setText(String.valueOf(myEditor.getCaretModel().getOffset()));

      DocumentAdapter valueSync = new DocumentAdapter() {
        boolean inSync;

        @Override
        protected void textChanged(DocumentEvent e) {
          if (inSync) return;
          inSync = true;
          String s = "<invalid>";
          JTextField f = null;
          try {
            if (e.getDocument() == myField.getDocument()) {
              f = myOffsetField;
              LogicalPosition p = getLogicalPosition();
              s = p == null ? s : String.valueOf(myEditor.logicalPositionToOffset(p));
            }
            else {
              f = myField;
              int offset = StringUtil.parseInt(myOffsetField.getText(), -1);
              LogicalPosition p = offset >= 0 ? myEditor.offsetToLogicalPosition(
                Math.min(myEditor.getDocument().getTextLength() - 1, offset)) : null;
              s = p == null ? s : String.format("%d:%d", p.line + 1, p.column + 1);
            }
            f.setText(s);
          }
          catch (IndexOutOfBoundsException ignored) {
            if (f != null) f.setText(s);
          }
          finally {
            inSync = false;
          }
        }
      };
      myField.getDocument().addDocumentListener(valueSync);
      myOffsetField.getDocument().addDocumentListener(valueSync);
    }

    return panel;
  }
}
