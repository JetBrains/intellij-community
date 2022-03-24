// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.TipsOfTheDayUsagesCollector;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.SystemInfo.isWin10OrNewer;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.ui.Gray.xD0;

public final class TipPanel extends JPanel implements DoNotAskOption {
  private static final JBColor DIVIDER_COLOR = new JBColor(0xd9d9d9, 0x515151);
  private static final int DEFAULT_WIDTH = 400;
  private static final int DEFAULT_HEIGHT = 200;
  private static final Logger LOG = Logger.getInstance(TipPanel.class);

  private @Nullable final Project myProject;
  private final TipUIUtil.Browser myBrowser;
  private final JLabel myPoweredByLabel;
  final AbstractAction myPreviousTipAction;
  final AbstractAction myNextTipAction;
  private @NotNull String myAlgorithm = "unknown";
  private @Nullable String myAlgorithmVersion = null;
  private List<TipAndTrickBean> myTips = Collections.emptyList();
  private TipAndTrickBean myCurrentTip = null;
  private JPanel myCurrentPromotion = null;

  public TipPanel(@Nullable final Project project) {
    setLayout(new BorderLayout());
    if (isWin10OrNewer && !StartupUiUtil.isUnderDarcula()) {
      setBorder(JBUI.Borders.customLine(xD0, 1, 0, 0, 0));
    }
    myProject = project;
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

    setTips(TipAndTrickBean.EP_NAME.getExtensionList());
  }

  void setTips(@NotNull List<TipAndTrickBean> list) {
    RecommendationDescription recommendation = ApplicationManager.getApplication().getService(TipsOrderUtil.class).sort(list);
    myTips = new ArrayList<>(recommendation.getTips());
    myAlgorithm = recommendation.getAlgorithm();
    myAlgorithmVersion = recommendation.getVersion();
    if (!isExperiment(myAlgorithm)) {
      myTips = TipsUsageManager.getInstance().filterShownTips(myTips);
    }
    showNext(true);
  }

  /**
   * We are running the experiment for research purposes and we want the experiment to be pure.
   * This requires disabling idea's filtering mechanism as this mechanism affects the experiment
   * results by modifying tips order.
   */
  private static boolean isExperiment(String algorithm) {
    return algorithm.endsWith("_SUMMER2020");
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

    TipUIUtil.openTipInBrowser(myCurrentTip, myBrowser);
    myPoweredByLabel.setText(TipUIUtil.getPoweredByText(myCurrentTip));
    myPoweredByLabel.setVisible(!isEmpty(myPoweredByLabel.getText()));
    setPromotionForCurrentTip();
    TipsOfTheDayUsagesCollector.triggerTipShown(tip, myAlgorithm, myAlgorithmVersion);
    TipsUsageManager.getInstance().fireTipShown(myCurrentTip);

    myPreviousTipAction.setEnabled(myTips.indexOf(myCurrentTip) > 0);
    myNextTipAction.setEnabled(myTips.indexOf(myCurrentTip) < myTips.size() - 1);
  }

  private void setPromotionForCurrentTip() {
    if (myProject == null || myProject.isDisposed()) return;
    if (myCurrentPromotion != null) {
      remove(myCurrentPromotion);
      myCurrentPromotion = null;
      myBrowser.getComponent().setBorder(JBUI.Borders.empty(8, 12));
    }
    List<JPanel> promotions = ContainerUtil.mapNotNull(TipAndTrickPromotionFactory.getEP_NAME().getExtensionList(),
                                                       factory -> factory.createPromotionPanel(myProject, myCurrentTip));
    if (!promotions.isEmpty()) {
      if (promotions.size() > 1) {
        LOG.warn("Found more than one promotion for tip " + myCurrentTip);
      }
      myCurrentPromotion = promotions.get(0);
      add(myCurrentPromotion, BorderLayout.NORTH);
      myBrowser.getComponent().setBorder(JBUI.Borders.empty(0, 12, 8, 12));
      removeTopMarginFromTipContent();
    }
    revalidate();
    repaint();
  }

  // Removes the top margin from first tag inside <body> of TipAndTrick text
  // to reduce the indent between promotion panel and Tip content
  private void removeTopMarginFromTipContent() {
    @Nls String tipText = myBrowser.getText();
    Matcher firstTagMatcher = Pattern.compile("<body>\\s*<\\w+", Pattern.CASE_INSENSITIVE).matcher(tipText);
    if (firstTagMatcher.find()) {
      int endOffset = firstTagMatcher.end();
      @SuppressWarnings("HardCodedStringLiteral")
      @Nls String editedTipText = new StringBuilder(tipText).insert(endOffset, " style=\"margin-top: 0px\"").toString();
      myBrowser.setText(editedTipText);
    }
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
