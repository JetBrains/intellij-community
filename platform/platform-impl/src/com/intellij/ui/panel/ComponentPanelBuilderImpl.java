// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.panel;

import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.openapi.ui.panel.GridBagPanelBuilder;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ComponentPanelBuilderImpl implements ComponentPanelBuilder, GridBagPanelBuilder {

  private final JComponent myComponent;

  private String myLabelText;
  private boolean myLabelOnTop;
  private String myComment;
  private boolean myCommentBelow = true;
  private String myHTDescription;
  private String myHTLinkText;
  private Runnable myHTAction;
  private boolean valid = true;

  ComponentPanelBuilderImpl(JComponent component) {
    myComponent = component;
  }

  @Override
  public ComponentPanelBuilder withLabel(@NotNull String labelText) {
    myLabelText = labelText;
    return this;
  }

  @Override
  public ComponentPanelBuilder moveLabelOnTop() {
    myLabelOnTop = true;
    return this;
  }

  @Override
  public ComponentPanelBuilder withComment(@NotNull String comment) {
    myComment = comment;
    valid = StringUtil.isNotEmpty(comment) && StringUtil.isEmpty(myHTDescription);
    return this;
  }

  @Override
  public ComponentPanelBuilder moveCommentRight() {
    myCommentBelow = false;
    return this;
  }

  @Override
  public ComponentPanelBuilder withTooltip(@NotNull String description) {
    myHTDescription = description;
    valid = StringUtil.isNotEmpty(description) && StringUtil.isEmpty(myComment);
    return this;
  }

  @Override
  public ComponentPanelBuilder withTooltipLink(@NotNull String linkText, @NotNull Runnable action) {
    myHTLinkText = linkText;
    myHTAction = action;
    return this;
  }

  @Override
  @NotNull
  public JPanel createPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
                                                   null, 0, 0);
    addToPanel(panel, gc);
    return panel;
  }

  @Override
  public boolean constrainsValid() {
    return valid;
  }

  @Override
  public int gridWidth() {
    return 2;
  }

  @Override
  public void addToPanel(JPanel panel, GridBagConstraints gc) {
    if (!constrainsValid()) return;

    gc.gridx = 0;
    gc.gridwidth = 1;
    gc.weightx = 0.0;
    gc.anchor = GridBagConstraints.LINE_START;

    if (StringUtil.isNotEmpty(myLabelText)) {
      JLabel lbl = new JLabel();
      LabeledComponent.TextWithMnemonic.fromTextWithMnemonic(myLabelText).setToLabel(lbl);
      lbl.setLabelFor(myComponent);

      if (myLabelOnTop) {
        gc.insets = JBUI.insetsBottom(4);
        gc.gridx++;
        panel.add(lbl, gc);
        gc.gridy++;
      } else {
        gc.insets = JBUI.insetsRight(8);
        panel.add(lbl, gc);
      }
    }

    gc.gridx += myLabelOnTop ? 0 : 1;
    gc.weightx = 1.0;
    gc.insets = JBUI.emptyInsets();

    JPanel componentPanel = new JPanel();
    componentPanel.setLayout(new BoxLayout(componentPanel, BoxLayout.X_AXIS));
    componentPanel.add(myComponent);

    JLabel comment = null;
    if (StringUtil.isNotEmpty(myComment)) {
      comment = new JLabel(myComment);
      comment.setForeground(Gray.x78);

      if (SystemInfo.isMac) {
        Font font = comment.getFont();
        float size = font.getSize2D();
        Font smallFont = font.deriveFont(size - 2.0f);
        comment.setFont(smallFont);
      }
    }

    if (StringUtil.isNotEmpty(myHTDescription)) {
      ContextHelpLabel lbl = StringUtil.isNotEmpty(myHTLinkText) && myHTAction != null ?
                             ContextHelpLabel.createWithLink(null, myHTDescription, myHTLinkText, myHTAction) :
                             ContextHelpLabel.create(myHTDescription);
      componentPanel.add(Box.createRigidArea(JBUI.size(7, 0)));
      componentPanel.add(lbl);
    }
    else if (comment != null && !myCommentBelow) {
      componentPanel.add(Box.createRigidArea(JBUI.size(14, 0)));
      componentPanel.add(comment);
    }

    panel.add(componentPanel, gc);

    if (comment != null && myCommentBelow) {
      gc.gridx = 1;
      gc.gridy++;
      gc.weightx = 0.0;
      gc.anchor = GridBagConstraints.NORTHWEST;
      gc.insets = getCommentInsets();
      panel.add(comment, gc);
    }

    gc.gridy++;
  }

  private Insets getCommentInsets() {
    if (myComponent instanceof JRadioButton || myComponent instanceof JCheckBox) {
      return JBUI.insets(0, 24, 0, 0);
    }
    else if (myComponent instanceof JTextField || myComponent instanceof EditorTextField ||
             myComponent instanceof JComboBox || myComponent instanceof ComponentWithBrowseButton) {
      return JBUI.insets(2, 6, 0, 0);
    }
    else if (myComponent instanceof JButton) {
      return JBUI.insets(0, 8, 0, 0);
    }
    else {
      return JBUI.insetsTop(9);
    }
  }
}
