// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.PatternUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class GotoLineNumberDialog extends DialogWrapper {
  private final Pattern myPattern = PatternUtil.compileSafe("\\s*(\\d+)?\\s*(?:[,:]?\\s*(\\d+)?)?\\s*", null);

  private JTextField myField;
  private JTextField myOffsetField;

  public GotoLineNumberDialog(Project project) {
    super(project, true);
    setTitle("Go to Line/Column");
  }

  private static boolean isInternal() {
    return ApplicationManagerEx.getApplicationEx().isInternal();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myField;
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  private String getText() {
    return myField.getText();
  }

  @Nullable
  protected final Coordinates getCoordinates() {
    Matcher m = myPattern.matcher(getText());
    if (!m.matches()) return null;

    int l = StringUtil.parseInt(m.group(1), getLine() + 1);
    int c = StringUtil.parseInt(m.group(2), -1);
    return l > 0 ? new Coordinates(l - 1, Math.max(0, c - 1)) : null;
  }

  protected abstract int getLine();
  protected abstract int getColumn();
  protected abstract int getOffset();
  protected abstract int getMaxOffset();
  protected abstract int coordinatesToOffset(@NotNull Coordinates coordinates);
  @NotNull
  protected abstract Coordinates offsetToCoordinates(int offset);

  @Override
  protected JComponent createNorthPanel() {
    class MyTextField extends JTextField {
      public MyTextField() {
        super("");
        addFocusListener(new FocusAdapter() {
          @Override
          public void focusGained(FocusEvent e) {
            if (!e.isTemporary()) {
              selectAll();
            }
          }
        });
      }

      @Override
      public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(200, d.height);
      }
    }

    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = JBUI.insets(4, 0, 8, 8);
    gbConstraints.fill = GridBagConstraints.VERTICAL;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.anchor = GridBagConstraints.EAST;
    JLabel label = new JLabel("[Line] [:column]:");
    panel.add(label, gbConstraints);

    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    myField = new MyTextField();
    panel.add(myField, gbConstraints);
    myField.setText(String.format("%d:%d", getLine() + 1, getColumn() + 1));

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
      myOffsetField.setText(String.valueOf(getOffset()));

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
              Coordinates p = getCoordinates();
              s = p == null ? s : String.valueOf(coordinatesToOffset(p));
            }
            else {
              f = myField;
              int offset = StringUtil.parseInt(myOffsetField.getText(), -1);
              Coordinates p = offset >= 0 ? offsetToCoordinates(Math.min(getMaxOffset() - 1, offset)) : null;
              s = p == null ? s : String.format("%d:%d", p.row + 1, p.column + 1);
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

  protected static class Coordinates {
    public final int row;
    public final int column;

    public Coordinates(int row, int column) {
      this.row = row;
      this.column = column;
    }
  }
}
