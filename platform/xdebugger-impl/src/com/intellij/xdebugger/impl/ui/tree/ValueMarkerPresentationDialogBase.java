// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.ui.*;
import com.intellij.util.text.UniqueNameGenerator;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Set;

/**
 * @author nik
 */
public abstract class ValueMarkerPresentationDialogBase extends DialogWrapper {
  private static final Color DEFAULT_COLOR = JBColor.RED;
  @NotNull private final Set<String> myExistingMarkups;
  private SimpleColoredComponent myColorSample;
  private Color myColor;
  private JPanel myMainPanel;
  private JTextField myLabelField;
  private FixedSizeButton myChooseColorButton;
  private JPanel mySamplePanel;
  private JPanel myErrorPanel;

  public ValueMarkerPresentationDialogBase(@Nullable Component parent, @Nullable String defaultText, @NotNull Collection<ValueMarkup> markups) {
    super(parent, true);
    setTitle("Select Object Label");
    setModal(true);
    myExistingMarkups = StreamEx.of(markups).map(ValueMarkup::getText).toSet();
    myLabelField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull final DocumentEvent e) {
        updateLabelSample();
      }
    });
    myChooseColorButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        final Color color = ColorChooser.chooseColor(myColorSample, "Choose Label Color", myColor);
        if (color != null) {
          myColor = color;
          updateLabelSample();
        }
      }
    });
    myColor = DEFAULT_COLOR;
    if (defaultText != null) {
      defaultText = UniqueNameGenerator.generateUniqueName(defaultText, myExistingMarkups);
      myLabelField.setText(defaultText.trim());
      updateLabelSample();
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myLabelField;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  private void updateLabelSample() {
    myColorSample.clear();
    SimpleTextAttributes attributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, myColor);
    String text = myLabelField.getText().trim();
    myColorSample.append(text, attributes);
    myErrorPanel.removeAll();
    if (myExistingMarkups.contains(text)) {
      myErrorPanel.add(new JLabel(XDebuggerBundle.message("xdebugger.mark.dialog.duplicate.warning"), UIUtil.getBalloonWarningIcon(),
                                  SwingConstants.LEFT));
    }
  }

  @Nullable
  public ValueMarkup getConfiguredMarkup() {
    final String text = myLabelField.getText().trim();
    return text.isEmpty() ? null : new ValueMarkup(text, myColor, null);
  }

  private void createUIComponents() {
    myColorSample = new SimpleColoredComponent();
    mySamplePanel = new JPanel(new BorderLayout());
    mySamplePanel.setBorder(BorderFactory.createEtchedBorder());
    mySamplePanel.add(BorderLayout.CENTER, myColorSample);
    myChooseColorButton = new FixedSizeButton(mySamplePanel);
  }
}
