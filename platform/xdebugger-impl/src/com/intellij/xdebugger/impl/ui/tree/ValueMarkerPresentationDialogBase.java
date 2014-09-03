/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.ui.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author nik
 */
public abstract class ValueMarkerPresentationDialogBase extends DialogWrapper {
  private static final Color DEFAULT_COLOR = JBColor.RED;
  private SimpleColoredComponent myColorSample;
  private Color myColor;
  private JPanel myMainPanel;
  private JTextField myLabelField;
  private FixedSizeButton myChooseColorButton;
  private JPanel mySamplePanel;

  public ValueMarkerPresentationDialogBase(@Nullable Component parent, final @Nullable String defaultText) {
    super(parent, true);
    setTitle("Select Object Label");
    setModal(true);
    myLabelField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateLabelSample();
      }
    });
    myChooseColorButton.addActionListener(new ActionListener() {
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
      myLabelField.setText(defaultText.trim());
      updateLabelSample();
    }
  }

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
    myColorSample.append(myLabelField.getText().trim(), attributes);
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
