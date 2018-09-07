// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.DialogWrapper.DoNotAskOption;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.SystemInfo.isWin10OrNewer;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.ui.Gray.xD0;
import static com.intellij.util.ui.UIUtil.isUnderDarcula;

public class TipPanel extends JPanel implements DoNotAskOption {
  private static final JBColor DIVIDER_COLOR = new JBColor(0xd9d9d9, 0x515151);
  private static final int DEFAULT_WIDTH = 400;
  private static final int DEFAULT_HEIGHT = 200;

  private final TipUIUtil.Browser myBrowser;
  private final JLabel myPoweredByLabel;
  private List<TipAndTrickBean> myTips = Collections.emptyList();

  public TipPanel() {
    setLayout(new BorderLayout());
    if (isWin10OrNewer && !isUnderDarcula()) {
      setBorder(JBUI.Borders.customLine(xD0, 1, 0, 0, 0));
    }
    myBrowser = TipUIUtil.createBrowser();
    myBrowser.getComponent().setBorder(JBUI.Borders.empty(8, 12));
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myBrowser.getComponent(), true);
    scrollPane.setBorder(JBUI.Borders.customLine(DIVIDER_COLOR, 0, 0, 1, 0));
    add(scrollPane, BorderLayout.CENTER);

    myPoweredByLabel = new JBLabel();
    myPoweredByLabel.setBorder(JBUI.Borders.empty(0, 10));
    myPoweredByLabel.setForeground(SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES.getFgColor());

    add(myPoweredByLabel, BorderLayout.SOUTH);
  }

  public void setTips(@NotNull List<TipAndTrickBean> list) {
    myTips = list;
  }

  @Override
  public Dimension getPreferredSize() {
    return new JBDimension(DEFAULT_WIDTH, DEFAULT_HEIGHT);
  }

  public void prevTip() {
    if (myTips.size() == 0) {
      myBrowser.setText(IdeBundle.message("error.tips.not.found", ApplicationNamesInfo.getInstance().getFullProductName()));
      return;
    }
    final GeneralSettings settings = GeneralSettings.getInstance();
    int lastTip = settings.getLastTip();

    final TipAndTrickBean tip;
    lastTip--;
    if (lastTip <= 0) {
      tip = myTips.get(myTips.size() - 1);
      lastTip = myTips.size();
    }
    else {
      tip = myTips.get(lastTip - 1);
    }

    setTip(tip, lastTip, myBrowser, settings);
  }

  private void setTip(TipAndTrickBean tip, int lastTip, TipUIUtil.Browser browser, GeneralSettings settings) {
    TipUIUtil.openTipInBrowser(tip, browser);
    myPoweredByLabel.setText(TipUIUtil.getPoweredByText(tip));
    myPoweredByLabel.setVisible(!isEmpty(myPoweredByLabel.getText()));
    settings.setLastTip(lastTip);
  }

  public void nextTip() {
    if (myTips.size() == 0) {
      myBrowser.setText(IdeBundle.message("error.tips.not.found", ApplicationNamesInfo.getInstance().getFullProductName()));
      return;
    }
    GeneralSettings settings = GeneralSettings.getInstance();
    int lastTip = settings.getLastTip();
    TipAndTrickBean tip;
    lastTip++;
    if (lastTip - 1 >= myTips.size()) {
      tip = myTips.get(0);
      lastTip = 1;
    }
    else {
      tip = myTips.get(lastTip - 1);
    }

    setTip(tip, lastTip, myBrowser, settings);
  }

  @Override
  public boolean canBeHidden() {
    return true;
  }

  @Override
  public boolean shouldSaveOptionsOnCancel() {
    return true;
  }

  @Override
  public boolean isToBeShown() {
    return !GeneralSettings.getInstance().isShowTipsOnStartup();
  }

  @Override
  public void setToBeShown(boolean toBeShown, int exitCode) {
    GeneralSettings.getInstance().setShowTipsOnStartup(!toBeShown);
  }

  @NotNull
  @Override
  public String getDoNotShowMessage() {
    return IdeBundle.message("checkbox.show.tips.on.startup");
  }
}
