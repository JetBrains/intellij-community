// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.icons.AllIcons;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.TipsOfTheDayUsagesCollector;
import com.intellij.ide.ui.text.StyledTextPane;
import com.intellij.ide.ui.text.paragraph.TextParagraph;
import com.intellij.ide.ui.text.parts.IllustrationTextPart;
import com.intellij.ide.ui.text.parts.RegularTextPart;
import com.intellij.ide.ui.text.parts.TextPart;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public final class TipPanel extends JPanel implements DoNotAskOption {
  public static final Key<String> CURRENT_TIP_KEY = Key.create("CURRENT_TIP");

  private static final Logger LOG = Logger.getInstance(TipPanel.class);

  private @Nullable final Project myProject;
  private @NotNull final JPanel myContentPanel;
  private @NotNull final JLabel mySubSystemLabel;
  private final StyledTextPane myTextPane;
  final AbstractAction myPreviousTipAction;
  final AbstractAction myNextTipAction;
  private @NotNull String myAlgorithm = "unknown";
  private @Nullable String myAlgorithmVersion = null;
  private List<TipAndTrickBean> myTips = Collections.emptyList();
  private TipAndTrickBean myCurrentTip = null;
  private JPanel myCurrentPromotion = null;

  private final Map<String, Boolean> myTipIdToLikenessState = new LinkedHashMap<>();
  private Boolean myCurrentLikenessState = null;

  public TipPanel(@Nullable final Project project, @NotNull final TipsSortingResult sortingResult, @NotNull Disposable parentDisposable) {
    setLayout(new BorderLayout());
    myProject = project;

    myContentPanel = new JPanel();
    myContentPanel.setBackground(TipUiSettings.getPanelBackground());
    myContentPanel.setLayout(new BoxLayout(myContentPanel, BoxLayout.Y_AXIS));

    mySubSystemLabel = new JLabel() {
      @Override
      public void updateUI() {
        super.updateUI();
        setFont(JBFont.label().lessOn(1.0f));
      }
    };
    mySubSystemLabel.setForeground(UIUtil.getLabelInfoForeground());
    mySubSystemLabel.setBorder(JBUI.Borders.emptyBottom((int)TextParagraph.SMALL_INDENT));
    mySubSystemLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    myContentPanel.add(mySubSystemLabel);

    myTextPane = new StyledTextPane();
    myTextPane.setBackground(TipUiSettings.getPanelBackground());
    myTextPane.setMargin(JBInsets.emptyInsets());
    myTextPane.setAlignmentX(Component.LEFT_ALIGNMENT);
    Disposer.register(parentDisposable, myTextPane);
    myContentPanel.add(myTextPane);

    JPanel centerPanel = new JPanel();
    centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
    Border insideBorder = TipUiSettings.getTipPanelBorder();
    Border outsideBorder = JBUI.Borders.customLine(TipUiSettings.getImageBorderColor(), 0, 0, 1, 0);
    centerPanel.setBorder(JBUI.Borders.compound(outsideBorder, insideBorder));
    centerPanel.setBackground(TipUiSettings.getPanelBackground());

    // scroll will not be shown in a regular case
    // it is required only for technical writers to test whether the content of the new do not exceed the bounds
    JBScrollPane scrollPane = new JBScrollPane(myContentPanel, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(JBUI.Borders.empty());
    centerPanel.add(scrollPane);

    centerPanel.add(Box.createRigidArea(new JBDimension(0, TipUiSettings.getFeedbackPanelTopIndent())));
    JPanel feedbackPanel = createFeedbackPanel();  // TODO: implement feedback sending
    centerPanel.add(feedbackPanel);

    add(centerPanel, BorderLayout.CENTER);

    myPreviousTipAction = new PreviousTipAction();
    myNextTipAction = new NextTipAction();

    setTips(sortingResult);
  }

  private JPanel createFeedbackPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
    panel.setBackground(TipUiSettings.getPanelBackground());
    panel.add(Box.createHorizontalGlue());

    JLabel label = new JLabel(IdeBundle.message("tip.of.the.day.feedback.question"));
    panel.add(label);
    panel.add(Box.createRigidArea(new JBDimension(8, 0)));

    ActionToolbarImpl toolbar = createFeedbackActionsToolbar();
    panel.add(toolbar);
    return panel;
  }

  private ActionToolbarImpl createFeedbackActionsToolbar() {
    AnAction likeAction = createFeedbackAction(IdeBundle.message("tip.of.the.day.feedback.like"),
                                               AllIcons.Ide.LikeDimmed, AllIcons.Ide.Like, AllIcons.Ide.LikeSelected, true);
    AnAction dislikeAction = createFeedbackAction(IdeBundle.message("tip.of.the.day.feedback.dislike"),
                                                  AllIcons.Ide.DislikeDimmed, AllIcons.Ide.Dislike, AllIcons.Ide.DislikeSelected, false);
    ActionGroup group = new DefaultActionGroup(likeAction, dislikeAction);

    ActionToolbarImpl toolbar = new ActionToolbarImpl("TipsAndTricksDialog", group, true) {
      @Override
      protected @NotNull ActionButton createToolbarButton(@NotNull AnAction action,
                                                          ActionButtonLook look,
                                                          @NotNull String place,
                                                          @NotNull Presentation presentation,
                                                          @NotNull Dimension minimumSize) {
        int buttonSize = getFeedbackButtonSize();
        Dimension size = new Dimension(buttonSize, buttonSize);
        ActionButton button = new ActionButton(action, presentation, place, size) {
          @Override
          protected void paintButtonLook(Graphics g) {
            // do not paint icon background
            getButtonLook().paintIcon(g, this, getIcon());
          }

          @Override
          public Dimension getPreferredSize() {
            return size;
          }

          @Override
          public Dimension getMaximumSize() {
            return size;
          }
        };
        int iconIndent = TipUiSettings.getFeedbackIconIndent();
        button.setBorder(BorderFactory.createEmptyBorder(iconIndent, iconIndent, iconIndent, iconIndent));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
      }

      @Override
      public @NotNull Dimension getPreferredSize() {
        int size = getFeedbackButtonSize();
        int buttonsCount = getActionGroup().getChildren(null).length;
        return new Dimension(size * buttonsCount, size);
      }

      @Override
      public Dimension getMinimumSize() {
        return getPreferredSize();
      }

      @Override
      public Dimension getMaximumSize() {
        return getPreferredSize();
      }
    };
    toolbar.setBackground(TipUiSettings.getPanelBackground());
    toolbar.setBorder(JBUI.Borders.empty());
    toolbar.setTargetComponent(this);
    return toolbar;
  }

  private static int getFeedbackButtonSize() {
    return AllIcons.Ide.Like.getIconWidth() + 2 * TipUiSettings.getFeedbackIconIndent();
  }

  private AnAction createFeedbackAction(@NlsActions.ActionText String text,
                                        Icon icon,
                                        Icon hoveredIcon,
                                        Icon selectedIcon,
                                        boolean isLike) {
    return new DumbAwareAction(text, null, icon) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        myCurrentLikenessState = isSelected() ? null : isLike;
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        boolean selected = isSelected();
        Presentation presentation = e.getPresentation();
        presentation.setIcon(selected ? selectedIcon : icon);
        presentation.setHoveredIcon(selected ? selectedIcon : hoveredIcon);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      private boolean isSelected() {
        return myCurrentLikenessState != null && myCurrentLikenessState == isLike;
      }
    };
  }

  void setTips(@NotNull TipsSortingResult sortingResult) {
    myTips = sortingResult.getTips();
    myAlgorithm = sortingResult.getAlgorithm();
    myAlgorithmVersion = sortingResult.getVersion();
    showNext(true);
  }

  private void showNext(boolean forward) {
    if (myTips.size() == 0) {
      setTipsNotFoundText();
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
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      List<TextParagraph> tipContent = TipUtils.loadAndParseTip(tip);
      ApplicationManager.getApplication().invokeLater(() -> doSetTip(tip, tipContent), ModalityState.stateForComponent(this));
    });
  }

  private void doSetTip(@NotNull TipAndTrickBean tip, @NotNull List<TextParagraph> tipContent) {
    saveCurrentTipLikenessState();
    myCurrentLikenessState = getLikenessState(tip);
    myCurrentTip = tip;

    if (Registry.is("tips.of.the.day.show.group.label", false)) {
      String groupName = TipUtils.getGroupDisplayNameForTip(tip);
      mySubSystemLabel.setText(ObjectUtils.notNull(groupName, ""));
      mySubSystemLabel.setVisible(groupName != null);
    }
    else {
      mySubSystemLabel.setVisible(false);
    }

    myTextPane.setParagraphs(tipContent);
    adjustTextPaneBorder(tipContent);
    setPromotionForCurrentTip();
    setTopBorder();
    revalidate();
    repaint();

    TipsOfTheDayUsagesCollector.triggerTipShown(tip, myAlgorithm, myAlgorithmVersion);
    TipsUsageManager.getInstance().fireTipShown(myCurrentTip);

    myPreviousTipAction.setEnabled(myTips.indexOf(myCurrentTip) > 0);
    myNextTipAction.setEnabled(myTips.indexOf(myCurrentTip) < myTips.size() - 1);
    ClientProperty.put(this, CURRENT_TIP_KEY, myCurrentTip.fileName);
  }

  private void adjustTextPaneBorder(List<TextParagraph> tipContent) {
    if (tipContent.isEmpty()) return;
    TextParagraph last = tipContent.get(tipContent.size() - 1);
    List<TextPart> parts = last.getTextParts();
    Border border = parts.size() == 1 && parts.get(0) instanceof IllustrationTextPart
                    ? null : JBUI.Borders.emptyBottom((int)TextParagraph.BIG_INDENT);
    myTextPane.setBorder(border);
  }

  private void setPromotionForCurrentTip() {
    if (myProject == null || myProject.isDisposed()) return;
    if (myCurrentPromotion != null) {
      remove(myCurrentPromotion);
      myCurrentPromotion = null;
    }
    List<JPanel> promotions = ContainerUtil.mapNotNull(TipAndTrickPromotionFactory.getEP_NAME().getExtensionList(),
                                                       factory -> factory.createPromotionPanel(myProject, myCurrentTip));
    if (!promotions.isEmpty()) {
      if (promotions.size() > 1) {
        LOG.warn("Found more than one promotion for tip " + myCurrentTip);
      }
      myCurrentPromotion = promotions.get(0);
      add(myCurrentPromotion, BorderLayout.NORTH);
    }
  }

  private void setTopBorder() {
    if (myCurrentPromotion == null && (SystemInfo.isWin10OrNewer || SystemInfo.isMac)) {
      setBorder(JBUI.Borders.customLine(TipUiSettings.getImageBorderColor(), 1, 0, 0, 0));
    }
    else {
      setBorder(null);
    }
  }

  private void setTipsNotFoundText() {
    String text = IdeBundle.message("error.tips.not.found", ApplicationNamesInfo.getInstance().getFullProductName());
    List<TextPart> parts = List.of(new RegularTextPart(text, false));
    myTextPane.setParagraphs(List.of(new TextParagraph(parts)));
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension baseSize = super.getPreferredSize();
    int height = Math.min(baseSize.height, TipUiSettings.getTipPanelMaxHeight());
    return new Dimension(getDefaultWidth(), height);
  }

  @Override
  public Dimension getMinimumSize() {
    Dimension baseSize = super.getMinimumSize();
    int height = Math.max(baseSize.height, TipUiSettings.getTipPanelMinHeight());
    return new Dimension(getDefaultWidth(), height);
  }

  private static int getDefaultWidth() {
    return TipUiSettings.getImageMaxWidth() + TipUiSettings.getTipPanelLeftIndent() + TipUiSettings.getTipPanelRightIndent();
  }

  Map<String, Boolean> getTipIdToLikenessStateMap() {
    saveCurrentTipLikenessState();
    return myTipIdToLikenessState;
  }

  private void saveCurrentTipLikenessState() {
    if (myCurrentTip != null) {
      String curTipId = myCurrentTip.getId();
      if (myCurrentLikenessState != TipsFeedback.getInstance().getLikenessState(curTipId)) {
        myTipIdToLikenessState.put(curTipId, myCurrentLikenessState);
      }
    }
  }

  private Boolean getLikenessState(TipAndTrickBean tip) {
    String tipId = tip.getId();
    if (myTipIdToLikenessState.containsKey(tipId)) {
      return myTipIdToLikenessState.get(tipId);
    }
    return TipsFeedback.getInstance().getLikenessState(tipId);
  }

  @NotNull JPanel getContentPanel() {
    return myContentPanel;
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
