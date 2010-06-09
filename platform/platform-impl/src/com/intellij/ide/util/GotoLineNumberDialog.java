/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.awt.*;

public class GotoLineNumberDialog extends DialogWrapper {
  private JTextField myField;
  private JTextField myOffsetField;
  private final Editor myEditor;

  public GotoLineNumberDialog(Project project, Editor editor){
    super(project, true);
    myEditor = editor;
    setTitle(IdeBundle.message("title.go.to.line"));
    init();
  }

  protected void doOKAction(){
    final LogicalPosition currentPosition = myEditor.getCaretModel().getLogicalPosition();
    int lineNumber = getLineNumber(currentPosition.line + 1);
    if (isInternal() && myOffsetField.getText().length() > 0) {
      try {
        final int offset = Integer.parseInt(myOffsetField.getText());
        if (offset < myEditor.getDocument().getTextLength()) {
          myEditor.getCaretModel().moveToOffset(offset);
          myEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
          myEditor.getSelectionModel().removeSelection();
          super.doOKAction();
        }
        return;
      }
      catch (NumberFormatException e) {
        return;
      }
    }

    if (lineNumber <= 0) return;

    int columnNumber = getColumnNumber(currentPosition.column);
    myEditor.getCaretModel().moveToLogicalPosition(new LogicalPosition(lineNumber - 1, columnNumber - 1));
    myEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    myEditor.getSelectionModel().removeSelection();
    super.doOKAction();
  }

  private int getColumnNumber(int defaultValue) {
    String text = getText();
    int columnIndex = columnSeparatorIndex(text);
    if (columnIndex == -1) return defaultValue;
    try {
      return Integer.parseInt(text.substring(columnIndex + 1));
    } catch (NumberFormatException e) {}
    return defaultValue;
  }

  private static int columnSeparatorIndex(final String text) {
    final int colonIndex = text.indexOf(':');
    return colonIndex >= 0 ? colonIndex : text.indexOf(',');
  }

  private int getLineNumber(int defaultLine) {
    try {
      String text = getText();
      int columnIndex = columnSeparatorIndex(text);
      text = columnIndex == -1 ? text : text.substring(0, columnIndex);
      if (text.trim().length() == 0) return defaultLine;
      return Integer.parseInt(text);
    } catch (NumberFormatException e) {
      return -1;
    }
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

    gbConstraints.insets = new Insets(4, 0, 8, 0);
    gbConstraints.fill = GridBagConstraints.VERTICAL;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.anchor = GridBagConstraints.EAST;
    JLabel label = new JLabel(IdeBundle.message("editbox.line.number"));
    panel.add(label, gbConstraints);

    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    myField = new MyTextField();
    panel.add(myField, gbConstraints);
    myField.setToolTipText(IdeBundle.message("tooltip.syntax.linenumber.columnnumber"));

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
    }

    return panel;
  }
}
