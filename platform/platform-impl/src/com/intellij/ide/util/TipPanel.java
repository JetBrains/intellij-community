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

import com.intellij.icons.AllIcons;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collections;
import java.util.List;

public class TipPanel extends JPanel {
  private static final int DEFAULT_WIDTH = 400;
  private static final int DEFAULT_HEIGHT = 200;

  private final JEditorPane myBrowser;
  private final JLabel myPoweredByLabel;
  private final List<TipAndTrickBean> myTips = ContainerUtil.newArrayList();

  public TipPanel() {
    setLayout(new BorderLayout());
    JLabel jlabel = new JLabel(AllIcons.General.Tip);
    jlabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
    JLabel label1 = new JLabel(IdeBundle.message("label.did.you.know"));
    Font font = label1.getFont();
    label1.setFont(font.deriveFont(Font.PLAIN, font.getSize() + 4));
    JPanel jpanel = new JPanel();
    jpanel.setLayout(new BorderLayout());
    jpanel.add(jlabel, BorderLayout.WEST);
    jpanel.add(label1, BorderLayout.CENTER);
    jpanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
    add(jpanel, BorderLayout.NORTH);
    myBrowser = TipUIUtil.createTipBrowser();
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myBrowser);
    add(scrollPane, BorderLayout.CENTER);

    JPanel southPanel = new JPanel(new BorderLayout());
    JCheckBox showOnStartCheckBox = new JCheckBox(IdeBundle.message("checkbox.show.tips.on.startup"), true);
    showOnStartCheckBox.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
    final GeneralSettings settings = GeneralSettings.getInstance();
    showOnStartCheckBox.setSelected(settings.isShowTipsOnStartup());
    showOnStartCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(@NotNull ItemEvent e) {
        settings.setShowTipsOnStartup(e.getStateChange() == ItemEvent.SELECTED);
      }
    });
    southPanel.add(showOnStartCheckBox, BorderLayout.WEST);

    myPoweredByLabel = new JBLabel();
    myPoweredByLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    myPoweredByLabel.setForeground(SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES.getFgColor());

    southPanel.add(myPoweredByLabel, BorderLayout.EAST);
    add(southPanel, BorderLayout.SOUTH);

    Collections.addAll(myTips, Extensions.getExtensions(TipAndTrickBean.EP_NAME));
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT);
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

  private void setTip(TipAndTrickBean tip, int lastTip, JEditorPane browser, GeneralSettings settings) {
    TipUIUtil.openTipInBrowser(tip, browser);
    myPoweredByLabel.setText(TipUIUtil.getPoweredByText(tip));
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
}
