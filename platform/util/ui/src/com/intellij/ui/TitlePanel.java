// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ui;

import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import java.awt.*;

public class TitlePanel extends CaptionPanel {
  private final JLabel myLabel;
  private @Nullable Icon myRegular;
  private @Nullable Icon myInactive;
  private boolean obeyPreferredWidth;
  private boolean isPopup = false;
  private boolean useHeaderInsets = false;
  private boolean isExperimentalUI = false;

  public TitlePanel() {
    this(null, null);
  }

  public TitlePanel(Icon regular, Icon inactive) {
    myRegular = regular;
    myInactive = inactive;

    myLabel = new EngravedLabel();
    myLabel.setForeground(JBColor.foreground());
    myLabel.setHorizontalAlignment(SwingConstants.CENTER);
    myLabel.setVerticalAlignment(SwingConstants.CENTER);

    add(myLabel, BorderLayout.CENTER);

    setActive(false);
  }

  @Override
  public void updateUI() {
    if (getParent() != null) {
      updateDimensions();
    }
    super.updateUI();
  }

  @Override
  public void setActive(final boolean active) {
    super.setActive(active);
    myLabel.setIcon(active ? myRegular : myInactive);
    if (isPopup) {
      myLabel.setForeground(JBUI.CurrentTheme.Popup.headerForeground(active));
    } else {
      myLabel.setForeground(active ? UIUtil.getLabelForeground() : UIUtil.getLabelDisabledForeground());
    }
  }

  public void setText(@Nls String titleText) {
    obeyPreferredWidth = BasicHTML.isHTMLString(titleText);
    myLabel.setText(titleText);
  }

  public void setRegularIcon(@Nullable Icon icon) {
    myRegular = icon;
  }

  public void setInactiveIcon(@Nullable Icon icon) {
    myInactive = icon;
  }

  @MagicConstant(intValues = {
    SwingConstants.LEFT,
    SwingConstants.CENTER,
    SwingConstants.RIGHT,
    SwingConstants.LEADING,
    SwingConstants.TRAILING})
  public void setHorizontalTextPosition(int textPosition) {
    myLabel.setHorizontalTextPosition(textPosition);
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(10, getPreferredSize().height);
  }

  @Override
  public Dimension getPreferredSize() {
    final String text = myLabel.getText();
    if (text == null || text.trim().isEmpty()) {
      return JBUI.emptySize();
    }

    final Dimension preferredSize = super.getPreferredSize();
    if (useHeaderInsets) {
      preferredSize.height = myLabel.getPreferredSize().height;
    } else {
      preferredSize.height = JBUI.CurrentTheme.Popup.headerHeight(containsSettingsControls());
    }
    int maxWidth = JBUIScale.scale(350);
    if (!obeyPreferredWidth && preferredSize.width > maxWidth) { // do not allow caption to extend parent container
      return new Dimension(maxWidth, preferredSize.height);
    }

    return preferredSize;
  }

  public @NotNull JLabel getLabel() {
    return myLabel;
  }

  @ApiStatus.Internal
  public void obeyPreferredWidth(boolean obeyWidth) {
    obeyPreferredWidth = obeyWidth;
  }

  @ApiStatus.Internal
  public void setPopupTitle(boolean isExperimentalUI) {
    isPopup = true;
    this.isExperimentalUI = isExperimentalUI;

    if (isExperimentalUI) {
      updateDimensions();
      useHeaderInsets = true;
    }
  }

  private void updateDimensions() {
    myLabel.setBorder(JBUI.Borders.empty(1, 10, 2, 10));

    if (isExperimentalUI && isPopup) {
      myLabel.setFont(JBFont.label().deriveFont(Font.BOLD));
      Insets insets = JBUI.CurrentTheme.Popup.headerInsets();
      setBorder(BorderFactory.createEmptyBorder(0, insets.left, 0, insets.right));
      myLabel.setBorder(BorderFactory.createEmptyBorder(insets.top, 0, insets.bottom, 0));
    }
  }
}
