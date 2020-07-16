// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.TipsOfTheDayUsagesCollector;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapper.DoNotAskOption;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.ide.util.TipAndTrickBean.findByFileName;
import static com.intellij.openapi.util.SystemInfo.isWin10OrNewer;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.ui.Gray.xD0;

public final class TipPanel extends JPanel implements DoNotAskOption {
  private static final JBColor DIVIDER_COLOR = new JBColor(0xd9d9d9, 0x515151);
  private static final int DEFAULT_WIDTH = 400;
  private static final int DEFAULT_HEIGHT = 200;
  private static final String LAST_SEEN_TIP_ID = "lastSeenTip";
  private static final String SEEN_TIPS = "seenTips";

  private final TipUIUtil.Browser myBrowser;
  private final JLabel myPoweredByLabel;
  final AbstractAction myPreviousTipAction;
  final AbstractAction myNextTipAction;
  private @NotNull String myAlgorithm = "unknown";
  private @Nullable String myAlgorithmVersion = null;
  private List<TipAndTrickBean> myTips = Collections.emptyList();
  private final List<String> mySeenIds = new ArrayList<>();
  private TipAndTrickBean myCurrentTip = null;

  public TipPanel() {
    setLayout(new BorderLayout());
    if (isWin10OrNewer && !StartupUiUtil.isUnderDarcula()) {
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

    myPreviousTipAction = new PreviousTipAction();
    myNextTipAction = new NextTipAction();

    mySeenIds.addAll(StringUtil.split(PropertiesComponent.getInstance().getValue(SEEN_TIPS, ""), ","));
    Collections.shuffle(mySeenIds);
    setTips(TipAndTrickBean.EP_NAME.getExtensionList());
  }

  void setTips(@NotNull List<? extends TipAndTrickBean> list) {
    RecommendationDescription recommendation = ApplicationManager.getApplication().getService(TipsOrderUtil.class).sort(list);
    myTips = new ArrayList<>(recommendation.getTips());
    myAlgorithm = recommendation.getAlgorithm();
    myAlgorithmVersion = recommendation.getVersion();
    for (String id : mySeenIds) {
      TipAndTrickBean tip = findByFileName(id);
      if (tip != null) {
        if (myTips.remove(tip)) {
          myTips.add(tip);   //move last seen to the end
        }
      }
    }
    if (TipDialog.wereTipsShownToday()) {
      TipAndTrickBean lastSeenTip = findByFileName(PropertiesComponent.getInstance().getValue(LAST_SEEN_TIP_ID));
      if (lastSeenTip != null && myTips.remove(lastSeenTip)) {
        myTips.add(0, lastSeenTip);
      }
    }
    showNext(true);
  }

  @Override
  public Dimension getPreferredSize() {
    return new JBDimension(DEFAULT_WIDTH, DEFAULT_HEIGHT);
  }

  private void showNext(boolean forward) {
    if (myTips.size() == 0) {
      myBrowser.setText(IdeBundle.message("error.tips.not.found", ApplicationNamesInfo.getInstance().getFullProductName()));
      return;
    }
    int index = myCurrentTip != null ? myTips.indexOf(myCurrentTip) : -1;
    if (forward) {
      if (index < myTips.size() - 1) {
        setTip(myTips.get(index + 1));
      }
    } else {
      if (index > 0) {
        setTip(myTips.get(index - 1));
      }
    }
  }

  private void setTip(@NotNull TipAndTrickBean tip) {
    myCurrentTip = tip;
    PropertiesComponent.getInstance().setValue(LAST_SEEN_TIP_ID, myCurrentTip.fileName);

    TipUIUtil.openTipInBrowser(myCurrentTip, myBrowser);
    myPoweredByLabel.setText(TipUIUtil.getPoweredByText(myCurrentTip));
    myPoweredByLabel.setVisible(!isEmpty(myPoweredByLabel.getText()));
    TipsOfTheDayUsagesCollector.triggerTipShown(tip, myAlgorithm, myAlgorithmVersion);

    if (!mySeenIds.contains(myCurrentTip.fileName)) {
      mySeenIds.add(myCurrentTip.fileName);
      if (mySeenIds.size() >= myTips.size()) {
        mySeenIds.clear();//It's useless to keep all possible IDs in 'last seen'
      }
      PropertiesComponent.getInstance().setValue(SEEN_TIPS, StringUtil.join(mySeenIds, ","));
    }

    myPreviousTipAction.setEnabled(myTips.indexOf(myCurrentTip) > 0);
    myNextTipAction.setEnabled(myTips.indexOf(myCurrentTip) < myTips.size() - 1);
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
    return GeneralSettings.getInstance().isShowTipsOnStartup();
  }

  @Override
  public void setToBeShown(boolean toBeShown, int exitCode) {
    GeneralSettings.getInstance().setShowTipsOnStartup(toBeShown);
  }

  @NotNull
  @Override
  public String getDoNotShowMessage() {
    return IdeBundle.message("checkbox.show.tips.on.startup");
  }

  private class PreviousTipAction extends AbstractAction {
    PreviousTipAction() {
      super(IdeBundle.message("action.previous.tip"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      TipsOfTheDayUsagesCollector.PREVIOUS_TIP.log();
      showNext(false);
    }
  }

  private class NextTipAction extends AbstractAction {
    NextTipAction() {
      super(IdeBundle.message("action.next.tip"));
      putValue(DialogWrapper.DEFAULT_ACTION, Boolean.TRUE);
      putValue(DialogWrapper.FOCUSED_ACTION, Boolean.TRUE); // myPreferredFocusedComponent
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      TipsOfTheDayUsagesCollector.NEXT_TIP.log();
      showNext(true);
    }
  }
}
