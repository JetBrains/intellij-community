// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicTextUI;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

final class TipPanel extends JPanel implements DoNotAskOption {
  public static final Key<String> CURRENT_TIP_KEY = Key.create("CURRENT_TIP");

  private static final Logger LOG = Logger.getInstance(TipPanel.class);

  private final @Nullable Project myProject;
  private final @NotNull JLabel mySubSystemLabel;
  private final StyledTextPane myTextPane;
  final AbstractAction myPreviousTipAction;
  final AbstractAction myNextTipAction;
  private @NotNull String myAlgorithm = "unknown";
  private @Nullable String myAlgorithmVersion = null;
  private List<TipAndTrickBean> myTips = Collections.emptyList();
  private TipAndTrickBean myCurrentTip = null;
  private JPanel myCurrentPromotion = null;

  private ActionToolbarImpl myFeedbackToolbar = null;
  private final Map<String, Boolean> myTipIdToLikenessState = new LinkedHashMap<>();
  private Boolean myCurrentLikenessState = null;

  TipPanel(final @Nullable Project project, final @NotNull TipsSortingResult sortingResult, @NotNull Disposable parentDisposable) {
    setLayout(new BorderLayout());
    myProject = project;

    @NotNull JPanel contentPanel = new JPanel();
    contentPanel.setBackground(TipUiSettings.getPanelBackground());
    contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

    mySubSystemLabel = new JLabel() {
      @Override
      public void updateUI() {
        super.updateUI();
        setFont(JBFont.label().lessOn(1.0f));
      }
    };
    mySubSystemLabel.setForeground(UIUtil.getLabelInfoForeground());
    mySubSystemLabel.setBorder(JBUI.Borders.emptyBottom((int)TextParagraph.SMALL_INDENT));
    mySubSystemLabel.setAlignmentX(LEFT_ALIGNMENT);
    contentPanel.add(mySubSystemLabel);

    myTextPane = new MyTextPane();
    myTextPane.putClientProperty("caretWidth", 0);
    myTextPane.setBackground(TipUiSettings.getPanelBackground());
    myTextPane.setMargin(JBInsets.emptyInsets());
    myTextPane.setAlignmentX(LEFT_ALIGNMENT);
    Disposer.register(parentDisposable, myTextPane);
    contentPanel.add(myTextPane);

    JPanel centerPanel = new JPanel();
    centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
    Border insideBorder = TipUiSettings.getTipPanelBorder();
    Border outsideBorder = JBUI.Borders.customLine(TipUiSettings.getImageBorderColor(), 0, 0, 1, 0);
    centerPanel.setBorder(JBUI.Borders.compound(outsideBorder, insideBorder));
    centerPanel.setBackground(TipUiSettings.getPanelBackground());

    // scroll will not be shown in a regular case
    // it is required only for technical writers to test whether the content of the new does not exceed the bounds
    JBScrollPane scrollPane = new JBScrollPane(contentPanel, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
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

    myFeedbackToolbar = createFeedbackActionsToolbar();
    panel.add(myFeedbackToolbar);
    return panel;
  }

  private ActionToolbarImpl createFeedbackActionsToolbar() {
    AnAction likeAction = createFeedbackAction(IdeBundle.message("tip.of.the.day.feedback.like"),
                                               AllIcons.Ide.LikeDimmed, AllIcons.Ide.Like, AllIcons.Ide.LikeSelected, true);
    AnAction dislikeAction = createFeedbackAction(IdeBundle.message("tip.of.the.day.feedback.dislike"),
                                                  AllIcons.Ide.DislikeDimmed, AllIcons.Ide.Dislike, AllIcons.Ide.DislikeSelected, false);
    AnAction[] actions = {likeAction, dislikeAction};

    ActionToolbarImpl toolbar = new ActionToolbarImpl("TipsAndTricksDialog", new DefaultActionGroup(actions), true) {
      @Override
      protected @NotNull ActionButton createToolbarButton(@NotNull AnAction action,
                                                          ActionButtonLook look,
                                                          @NotNull String place,
                                                          @NotNull Presentation presentation,
                                                          @NotNull Supplier<? extends @NotNull Dimension> minimumSize) {
        ActionButton button = new ActionButton(action, presentation, place, getFeedbackButtonSize()) {
          @Override
          protected void paintButtonLook(Graphics g) {
            // do not paint an icon background
            getButtonLook().paintIcon(g, this, getIcon());
          }

          @Override
          public Dimension getPreferredSize() {
            return getFeedbackButtonSize();
          }

          @Override
          public Dimension getMaximumSize() {
            return getFeedbackButtonSize();
          }
        };
        int iconIndent = TipUiSettings.getFeedbackIconIndent();
        button.setBorder(BorderFactory.createEmptyBorder(iconIndent, iconIndent, iconIndent, iconIndent));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
      }

      @Override
      protected boolean canReuseActionButton(@NotNull ActionButton oldActionButton, @NotNull Presentation newPresentation) {
        return true;
      }

      @Override
      public @NotNull Dimension getPreferredSize() {
        Dimension size = getFeedbackButtonSize();
        int buttonsCount = actions.length;
        return new Dimension(size.width * buttonsCount, size.height);
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

  private static Dimension getFeedbackButtonSize() {
    int dim = AllIcons.Ide.Like.getIconWidth() + 2 * TipUiSettings.getFeedbackIconIndent();
    return new Dimension(dim, dim);
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
    if (myTips.isEmpty()) {
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
    IdeFrame projectFrame = myProject != null ? WindowManager.getInstance().getIdeFrame(myProject) : null;
    IdeFrame welcomeFrame = WelcomeFrame.getInstance();
    Component contextComponent = this.isShowing() ? this :
                                 projectFrame != null ? projectFrame.getComponent() :
                                 welcomeFrame != null ? welcomeFrame.getComponent() : null;
    if (contextComponent == null) {
      LOG.warn("Not found context component");
    }
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      List<TextParagraph> tipContent = TipUtils.loadAndParseTip(tip, contextComponent);
      ApplicationManager.getApplication().invokeLater(() -> doSetTip(tip, tipContent), ModalityState.stateForComponent(this));
    });
  }

  private void doSetTip(@NotNull TipAndTrickBean tip, @NotNull List<? extends TextParagraph> tipContent) {
    saveCurrentTipLikenessState();
    myCurrentLikenessState = getLikenessState(tip);
    myFeedbackToolbar.updateActionsAsync();
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

  private void adjustTextPaneBorder(List<? extends TextParagraph> tipContent) {
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
    if (myCurrentTip != null && myCurrentLikenessState != getLikenessState(myCurrentTip)) {
      myTipIdToLikenessState.put(myCurrentTip.getId(), myCurrentLikenessState);
    }
  }

  private Boolean getLikenessState(@NotNull TipAndTrickBean tip) {
    String tipId = tip.getId();
    if (myTipIdToLikenessState.containsKey(tipId)) {
      return myTipIdToLikenessState.get(tipId);
    }
    return TipsFeedback.getInstance().getLikenessState(tipId);
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

  @Override
  public @NotNull String getDoNotShowMessage() {
    return IdeBundle.message("checkbox.show.tips.on.startup");
  }

  private static class MyTextPane extends StyledTextPane {
    @Override
    public void redraw() {
      super.redraw();
      View root = getRootView();
      // request layout the text with the width, according to scroll bar, is shown
      // it will be extended if scroll bar is not required in a result
      int width = TipUiSettings.getImageMaxWidth() - JBUI.scale(14);
      root.setSize(width, root.getPreferredSpan(View.Y_AXIS));
    }

    @Override
    public Dimension getPreferredSize() {
      // take size from the root view directly, because base implementation is resetting the root view size
      // if current bounds are empty (component is not added to screen)
      View root = getRootView();
      Dimension dim = new Dimension((int)root.getPreferredSpan(View.X_AXIS), (int)root.getPreferredSpan(View.Y_AXIS));
      JBInsets.addTo(dim, getInsets());
      return dim;
    }

    private View getRootView() {
      return ((BasicTextUI)ui).getRootView(this);
    }
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
