// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.idea.ActionsBundle;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.notification.EventLog;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.panel.ProgressPanel;
import com.intellij.openapi.ui.panel.ProgressPanelBuilder;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonHandler;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.NlsContexts.PopupContent;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.reference.SoftReference;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.Alarm;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.*;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.*;

public final class InfoAndProgressPanel extends JPanel implements CustomStatusBarWidget, UISettingsListener {
  public static final Object FAKE_BALLOON = new Object();

  private final ProcessPopup myPopup;
  private final ProcessBalloon myBalloon = new ProcessBalloon(3);

  private final JPanel myRefreshAndInfoPanel = new JPanel();
  private final InlineProgressPanel myInlinePanel = new InlineProgressPanel();
  private final NotNullLazyValue<AsyncProcessIcon> myProgressIcon = NotNullLazyValue.lazy(() -> {
    AsyncProcessIcon icon = new AsyncProcessIcon("Background process");
    icon.setOpaque(false);

    icon.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        handle(e);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        handle(e);
      }
    });

    icon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    icon.setBorder(JBUI.CurrentTheme.StatusBar.Widget.border());
    icon.setToolTipText(ActionsBundle.message("action.ShowProcessWindow.double.click"));
    return icon;
  });

  private final List<ProgressIndicatorEx> myOriginals = new ArrayList<>();
  private final List<TaskInfo> myInfos = new ArrayList<>();
  private final Map<InlineProgressIndicator, ProgressIndicatorEx> myInlineToOriginal = new HashMap<>();
  private final Map<ProgressIndicatorEx, Set<MyInlineProgressIndicator>> myOriginalToInlines = new HashMap<>();

  private final MergingUpdateQueue myUpdateQueue;
  private final Alarm myQueryAlarm = new Alarm();

  private boolean myShouldClosePopupAndOnProcessFinish;

  private final @NotNull JLabel myRefreshIcon;
  private final @NotNull StatusPanel myStatusPanel;

  private String myCurrentRequestor;
  private boolean myDisposed;
  private WeakReference<Balloon> myLastShownBalloon;
  private JComponent myCentralComponent;
  private boolean myShowNavBar;

  private final Set<InlineProgressIndicator> myDirtyIndicators = new ReferenceOpenHashSet<>();
  private final Update myUpdateIndicators = new Update("UpdateIndicators", false, 1) {
    @Override
    public void run() {
      List<InlineProgressIndicator> indicators;
      synchronized (myDirtyIndicators) {
        indicators = new ArrayList<>(myDirtyIndicators);
        myDirtyIndicators.clear();
      }
      for (InlineProgressIndicator indicator : indicators) {
        indicator.updateAndRepaint();
      }
    }
  };

  InfoAndProgressPanel(UISettings uiSettings) {
    setOpaque(false);
    setBorder(JBUI.Borders.empty());

    myRefreshAndInfoPanel.setLayout(new BorderLayout());
    myRefreshAndInfoPanel.setOpaque(false);

    myShowNavBar = ExperimentalUI.isNewUI() && uiSettings.getShowNavigationBar();

    myRefreshIcon = new JLabel(new AnimatedIcon.FS());
    myRefreshIcon.setVisible(false);

    myStatusPanel = new StatusPanel();

    if (!myShowNavBar) {
      myRefreshAndInfoPanel.add(myRefreshIcon, BorderLayout.WEST);
      myRefreshAndInfoPanel.add(myStatusPanel, BorderLayout.CENTER);
    }

    myUpdateQueue = new MergingUpdateQueue("Progress indicator", 50, true, MergingUpdateQueue.ANY_COMPONENT);
    myPopup = new ProcessPopup(this);

    setRefreshVisible(false);

    setLayout(new InlineLayout());
    add(myRefreshAndInfoPanel);
    add(myInlinePanel);

    myRefreshAndInfoPanel.revalidate();
    myRefreshAndInfoPanel.repaint();

    runOnProgressRelatedChange(this::updateProgressIcon, this, true);
  }

  private void runOnProgressRelatedChange(@NotNull Runnable runnable, @NotNull Disposable parentDisposable, boolean powerSaveMode) {
    synchronized (myOriginals) {
      if (!myDisposed) {
        MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(parentDisposable);
        if (powerSaveMode) {
          connection.subscribe(PowerSaveMode.TOPIC, () -> UIUtil.invokeLaterIfNeeded(runnable));
        }
        connection.subscribe(ProgressSuspender.TOPIC, new ProgressSuspender.SuspenderListener() {
          @Override
          public void suspendableProgressAppeared(@NotNull ProgressSuspender suspender) {
            UIUtil.invokeLaterIfNeeded(runnable);
          }

          @Override
          public void suspendedStatusChanged(@NotNull ProgressSuspender suspender) {
            UIUtil.invokeLaterIfNeeded(runnable);
          }
        });
      }
    }
  }

  private void handle(@NotNull MouseEvent e) {
    if (UIUtil.isActionClick(e, MouseEvent.MOUSE_PRESSED)) {
      triggerPopupShowing();
    }
  }

  @Override
  public @NotNull String ID() {
    return "InfoAndProgress";
  }

  @Override
  public WidgetPresentation getPresentation() {
    return null;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
  }

  @ApiStatus.Experimental
  public void setCentralComponent(@Nullable JComponent component) {
    if (myShowNavBar) {
      BorderLayout layout = (BorderLayout)myRefreshAndInfoPanel.getLayout();
      Component c = layout.getLayoutComponent(BorderLayout.CENTER);
      if (c != null) {
        myRefreshAndInfoPanel.remove(c);
        myCentralComponent = null;
      }

      if (component != null) {
        myRefreshAndInfoPanel.add(component, BorderLayout.CENTER);
      }
    }
    myCentralComponent = component;
  }

  @Override
  public void dispose() {
    setRefreshVisible(false);
    synchronized (myOriginals) {
      restoreEmptyStatus();
      for (InlineProgressIndicator indicator : myInlineToOriginal.keySet()) {
        Disposer.dispose(indicator);
      }
      myInlineToOriginal.clear();
      myOriginalToInlines.clear();

      myDisposed = true;
    }

    GuiUtils.removePotentiallyLeakingReferences(myRefreshIcon);
    myInfos.clear();
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  @NotNull
  List<Pair<TaskInfo, ProgressIndicator>> getBackgroundProcesses() {
    synchronized (myOriginals) {
      if (myOriginals.isEmpty()) return Collections.emptyList();

      List<Pair<TaskInfo, ProgressIndicator>> result = new ArrayList<>(myOriginals.size());
      for (int i = 0; i < myOriginals.size(); i++) {
        result.add(Pair.create(myInfos.get(i), myOriginals.get(i)));
      }

      return Collections.unmodifiableList(result);
    }
  }

  void addProgress(@NotNull ProgressIndicatorEx original, @NotNull TaskInfo info) {
    ApplicationManager.getApplication().assertIsDispatchThread(); // openProcessPopup may require dispatch thread

    synchronized (myOriginals) {
      myOriginals.add(original);
      myInfos.add(info);

      MyInlineProgressIndicator expanded = createInlineDelegate(info, original, false);
      MyInlineProgressIndicator compact = createInlineDelegate(info, original, true);

      myPopup.addIndicator(expanded);
      myBalloon.addIndicator(getRootPane(), compact);

      updateProgressIcon();

      if (myOriginals.size() == 1) {
        myInlinePanel.updateState(compact);
      }
      else {
        myInlinePanel.updateState();
      }
      if (myInfos.size() > 1 && Registry.is("ide.windowSystem.autoShowProcessPopup")) {
        openProcessPopup(false);
      }

      if (original.isFinished(info)) {
        // already finished, progress might not send another finished message
        removeProgress(expanded);
        removeProgress(compact);
        return;
      }

      runQuery();
    }
  }

  private boolean hasProgressIndicators() {
    synchronized (myOriginals) {
      return !myOriginals.isEmpty();
    }
  }

  private void removeProgress(@NotNull MyInlineProgressIndicator progress) {
    synchronized (myOriginals) {
      if (!myInlineToOriginal.containsKey(progress)) return; // already disposed

      boolean last = myOriginals.size() == 1;

      if (!progress.isCompact()) {
        myPopup.removeIndicator(progress);
      }

      ProgressIndicatorEx original = removeFromMaps(progress);
      if (myOriginals.contains(original)) {
        Disposer.dispose(progress);
        if (progress.isCompact()) {
          myBalloon.removeIndicator(getRootPane(), progress);
        }
        return;
      }

      if (last) {
        myInlinePanel.updateState(null);
        if (myShouldClosePopupAndOnProcessFinish) {
          hideProcessPopup();
        }
      }
      else {
        myInlinePanel.updateState(createInlineDelegate(myInfos.get(0), myOriginals.get(0), true));
      }

      runQuery();
    }
    Disposer.dispose(progress);
    if (progress.isCompact()) {
      myBalloon.removeIndicator(getRootPane(), progress);
    }
  }

  private ProgressIndicatorEx removeFromMaps(@NotNull MyInlineProgressIndicator progress) {
    ProgressIndicatorEx original = myInlineToOriginal.get(progress);

    myInlineToOriginal.remove(progress);
    synchronized (myDirtyIndicators) {
      myDirtyIndicators.remove(progress);
    }

    Set<MyInlineProgressIndicator> set = myOriginalToInlines.get(original);
    if (set != null) {
      set.remove(progress);
      if (set.isEmpty()) {
        set = null;
        myOriginalToInlines.remove(original);
      }
    }
    if (set == null) {
      int originalIndex = myOriginals.indexOf(original);
      myOriginals.remove(originalIndex);
      myInfos.remove(originalIndex);
    }

    return original;
  }

  private void openProcessPopup(boolean requestFocus) {
    synchronized (myOriginals) {
      if (myPopup.isShowing()) return;
      myPopup.show(requestFocus);
      myShouldClosePopupAndOnProcessFinish = hasProgressIndicators();
      myInlinePanel.updateState(true);
    }
  }

  void hideProcessPopup() {
    synchronized (myOriginals) {
      if (!myPopup.isShowing()) return;
      myPopup.hide();
      myInlinePanel.updateState(false);
    }
  }

  @Nullable
  @NlsContexts.StatusBarText
  public String setText(@Nullable @NlsContexts.StatusBarText String text, @Nullable String requestor) {
    if (myShowNavBar) return text;

    if (Strings.isEmpty(text) &&!Objects.equals(requestor, myCurrentRequestor) && !EventLog.LOG_REQUESTOR.equals(requestor)) {
      return myStatusPanel.getText();
    }

    boolean logMode = myStatusPanel.updateText(EventLog.LOG_REQUESTOR.equals(requestor) ? "" : text);
    myCurrentRequestor = logMode ? EventLog.LOG_REQUESTOR : requestor;
    return text;
  }

  void setRefreshVisible(boolean visible) {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (!myShowNavBar) myRefreshIcon.setVisible(visible);
    });
  }

  void setRefreshToolTipText(@NlsContexts.Tooltip String tooltip) {
    if (!myShowNavBar) {
      myRefreshIcon.setToolTipText(tooltip);
    }
  }

  public BalloonHandler notifyByBalloon(@NotNull MessageType type,
                                        @NotNull @PopupContent String htmlBody,
                                        @Nullable Icon icon,
                                        @Nullable HyperlinkListener listener) {
    Balloon balloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
      htmlBody.replace("\n", "<br>"),
      icon != null ? icon : type.getDefaultIcon(),
      type.getPopupBackground(),
      listener).createBalloon();

    SwingUtilities.invokeLater(() -> {
      Balloon oldBalloon = SoftReference.dereference(myLastShownBalloon);
      if (oldBalloon != null) {
        balloon.setAnimationEnabled(false);
        oldBalloon.setAnimationEnabled(false);
        oldBalloon.hide();
      }
      myLastShownBalloon = new WeakReference<>(balloon);

      Component comp = this;
      if (comp.isShowing()) {
        int offset = comp.getHeight() / 2;
        Point point = new Point(comp.getWidth() - offset, comp.getHeight() - offset);
        balloon.show(new RelativePoint(comp, point), Balloon.Position.above);
      }
      else {
        JRootPane rootPane = SwingUtilities.getRootPane(comp);
        if (rootPane != null && rootPane.isShowing()) {
          Container contentPane = rootPane.getContentPane();
          Rectangle bounds = contentPane.getBounds();
          Point target = StartupUiUtil.getCenterPoint(bounds, JBUI.size(1, 1));
          target.y = bounds.height - 3;
          balloon.show(new RelativePoint(contentPane, target), Balloon.Position.above);
        }
      }
    });

    return () -> SwingUtilities.invokeLater(balloon::hide);
  }

  private @NotNull MyInlineProgressIndicator createInlineDelegate(@NotNull TaskInfo info,
                                                                  @NotNull ProgressIndicatorEx original,
                                                                  boolean compact) {
    Set<MyInlineProgressIndicator> inlines = myOriginalToInlines.computeIfAbsent(original, __ -> new HashSet<>());
    if (!inlines.isEmpty()) {
      for (MyInlineProgressIndicator eachInline : inlines) {
        if (eachInline.isCompact() == compact) {
          return eachInline;
        }
      }
    }

    MyInlineProgressIndicator inline = compact ? new MyInlineProgressIndicator(info, original) :
                                       new ProgressPanelProgressIndicator(info, original);
    myInlineToOriginal.put(inline, original);
    inlines.add(inline);

    if (compact) {
      inline.getComponent().addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          handle(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
          handle(e);
        }
      });
    }

    return inline;
  }

  private void triggerPopupShowing() {
    if (myPopup.isShowing()) {
      hideProcessPopup();
    }
    else {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("bg.progress.window.show.from.status.bar");
      openProcessPopup(true);
    }
  }

  private void updateProgressIcon() {
    AsyncProcessIcon progressIcon = myProgressIcon.isComputed() ? myProgressIcon.getValue() : null;
    if (progressIcon == null) {
      return;
    }

    if (myOriginals.isEmpty() || PowerSaveMode.isEnabled() ||
        myOriginals.stream().map(ProgressSuspender::getSuspender).allMatch(s -> s != null && s.isSuspended())) {
      progressIcon.suspend();
    }
    else {
      progressIcon.resume();
    }
  }

  private void restoreEmptyStatus() {
    removeAll();
    setLayout(new BorderLayout());
    add(myRefreshAndInfoPanel, BorderLayout.CENTER);

    myRefreshAndInfoPanel.revalidate();
    myRefreshAndInfoPanel.repaint();
  }

  boolean isProcessWindowOpen() {
    return myPopup.isShowing();
  }

  void setProcessWindowOpen(boolean open) {
    if (open) {
      openProcessPopup(true);
    }
    else {
      hideProcessPopup();
    }
  }

  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    myShowNavBar = ExperimentalUI.isNewUI() && uiSettings.getShowNavigationBar();

    BorderLayout layout = (BorderLayout)myRefreshAndInfoPanel.getLayout();
    Component c = layout.getLayoutComponent(BorderLayout.CENTER);
    if (c != null) {
      myRefreshAndInfoPanel.remove(c);
    }

    c = layout.getLayoutComponent(BorderLayout.WEST);
    if (c != null) {
      myRefreshAndInfoPanel.remove(c);
    }

    if (myShowNavBar) {
      if (myCentralComponent != null) {
        myRefreshAndInfoPanel.add(myCentralComponent, BorderLayout.CENTER);
        myCentralComponent.updateUI();
      }
    }
    else {
      myRefreshAndInfoPanel.add(myRefreshIcon, BorderLayout.WEST);
      myRefreshAndInfoPanel.add(myStatusPanel, BorderLayout.CENTER);

      myRefreshIcon.updateUI();
      myStatusPanel.updateUI();
    }
  }

  private class ProgressPanelProgressIndicator extends MyInlineProgressIndicator {
    private final ProgressPanel myProgressPanel;
    private final InplaceButton myCancelButton;
    private final InplaceButton mySuspendButton;
    private final Runnable mySuspendUpdateRunnable;

    ProgressPanelProgressIndicator(@NotNull TaskInfo task, @NotNull ProgressIndicatorEx original) {
      super(false, task, original);

      myProgressPanel = Objects.requireNonNull(ProgressPanel.getProgressPanel(myProgress));
      ClientProperty.put(myComponent, ProcessPopup.KEY, myProgressPanel);

      myCancelButton = Objects.requireNonNull(myProgressPanel.getCancelButton());
      myCancelButton.setPainting(task.isCancellable());

      mySuspendButton = Objects.requireNonNull(myProgressPanel.getSuspendButton());
      mySuspendUpdateRunnable = createSuspendUpdateRunnable(mySuspendButton);

      setProcessNameValue(task.getTitle());

      // TODO: update javadoc for ProgressIndicator
    }

    @Override
    protected @NotNull Runnable createSuspendUpdateRunnable(@NotNull InplaceButton suspendButton) {
      suspendButton.setVisible(false);

      return () -> {
        ProgressSuspender suspender = getSuspender();
        suspendButton.setVisible(suspender != null);

        if (suspender != null && (myProgressPanel.getState() == ProgressPanel.State.PAUSED) != suspender.isSuspended()) {
          myProgressPanel.setState(suspender.isSuspended() ? ProgressPanel.State.PAUSED : ProgressPanel.State.PLAYING);
          updateProgressIcon();
        }
      };
    }

    @Override
    protected boolean canCheckPowerSaveMode() {
      return false;
    }

    @Override
    protected @NotNull JPanel createComponent() {
      ProgressPanelBuilder builder = new ProgressPanelBuilder(myProgress).withTopSeparator();
      builder.withText2();

      builder.withCancel(this::cancelRequest);

      Runnable suspendRunnable = createSuspendRunnable();
      builder.withPause(suspendRunnable).withResume(suspendRunnable);

      return builder.createPanel();
    }

    @Override
    protected @Nullable String getTextValue() {
      return myProgressPanel.getCommentText();
    }

    @Override
    protected void setTextValue(@NotNull String text) {
      myProgressPanel.setCommentText(text);
    }

    @Override
    protected void setTextEnabled(boolean value) {
      myProgressPanel.setCommentEnabled(value);
    }

    @Override
    protected void setText2Value(@NotNull String text) {
      myProgressPanel.setText2(text);
    }

    @Override
    protected void setText2Enabled(boolean value) {
      myProgressPanel.setText2Enabled(value);
    }

    @Override
    protected void setProcessNameValue(@NotNull String text) {
      myProgressPanel.setLabelText(text);
    }

    @Override
    public void updateProgressNow() {
      super.updateProgressNow();
      mySuspendUpdateRunnable.run();
      updateCancelButton(mySuspendButton, myCancelButton);
    }
  }

  class MyInlineProgressIndicator extends InlineProgressIndicator {
    private ProgressIndicatorEx myOriginal;
    PresentationModeProgressPanel myPresentationModeProgressPanel;
    Balloon myPresentationModeBalloon;
    boolean myPresentationModeShowBalloon;

    MyInlineProgressIndicator(@NotNull TaskInfo task, @NotNull ProgressIndicatorEx original) {
      this(true, task, original);
    }

    MyInlineProgressIndicator(boolean compact, @NotNull TaskInfo task, @NotNull ProgressIndicatorEx original) {
      super(compact, task);
      myOriginal = original;
      original.addStateDelegate(this);
      addStateDelegate(new AbstractProgressIndicatorExBase() {
        @Override
        public void cancel() {
          super.cancel();
          updateProgress();
        }
      });
      runOnProgressRelatedChange(this::queueProgressUpdate, this, canCheckPowerSaveMode());
    }

    @Override
    protected void createCompactTextAndProgress(@NotNull JPanel component) {
      myText.setTextAlignment(Component.RIGHT_ALIGNMENT);
      myText.recomputeSize();
      UIUtil.setCursor(myText, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      UIUtil.setCursor(myProgress, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      super.createCompactTextAndProgress(component);
      ((JComponent)myProgress.getParent()).setBorder(JBUI.Borders.empty(0, 8, 0, 4));
    }

    protected boolean canCheckPowerSaveMode() {
      return true;
    }

    @Override
    public String getText() {
      String text = StringUtil.notNullize(super.getText());
      ProgressSuspender suspender = getSuspender();
      return suspender != null && suspender.isSuspended() ? suspender.getSuspendedText() : text;
    }

    @Override
    protected @NotNull JBIterable<ProgressButton> createEastButtons() {
      return JBIterable.of(createSuspendButton()).append(super.createEastButtons());
    }

    protected void updateCancelButton(@NotNull InplaceButton suspend, @NotNull InplaceButton cancel) {
      boolean painting = getInfo().isCancellable() && !isStopping();
      cancel.setPainting(painting);
      cancel.setVisible(painting || !suspend.isVisible());
    }

    @NotNull
    JBIterable<ProgressButton> createPresentationButtons() {
      ProgressButton suspend = createSuspendButton();
      ProgressButton cancel = createCancelButton();
      return JBIterable.of(suspend).append(new ProgressButton(cancel.button, () -> updateCancelButton(suspend.button, cancel.button)));
    }

    @NotNull
    private ProgressButton createSuspendButton() {
      InplaceButton suspendButton = new InplaceButton("", AllIcons.Actions.Pause, e -> createSuspendRunnable().run()).setFillBg(false);
      return new ProgressButton(suspendButton, createSuspendUpdateRunnable(suspendButton));
    }

    @NotNull
    protected Runnable createSuspendRunnable() {
      return () -> {
        ProgressSuspender suspender = getSuspender();
        if (suspender == null) {
          return;
        }
        if (suspender.isSuspended()) {
          suspender.resumeProcess();
        }
        else {
          suspender.suspendProcess(null);
        }
        (suspender.isSuspended() ? UIEventLogger.ProgressPaused : UIEventLogger.ProgressResumed).log();
      };
    }

    @NotNull
    protected Runnable createSuspendUpdateRunnable(@NotNull InplaceButton suspendButton) {
      suspendButton.setVisible(false);

      return () -> {
        ProgressSuspender suspender = getSuspender();
        suspendButton.setVisible(suspender != null);
        if (suspender != null) {
          String toolTipText = suspender.isSuspended()
                               ? IdeBundle.message("comment.text.resume")
                               : IdeBundle.message("comment.text.pause");
          if (!toolTipText.equals(suspendButton.getToolTipText())) {
            updateProgressIcon();
            if (suspender.isSuspended()) {
              showResumeIcons(suspendButton);
            }
            else {
              showPauseIcons(suspendButton);
            }
            suspendButton.setToolTipText(toolTipText);
          }
        }
      };
    }

    private void showPauseIcons(InplaceButton button) {
      setIcons(button, AllIcons.Process.ProgressPauseSmall, AllIcons.Process.ProgressPause, AllIcons.Process.ProgressPauseSmallHover,
               AllIcons.Process.ProgressPauseHover);
    }

    private void showResumeIcons(InplaceButton button) {
      setIcons(button, AllIcons.Process.ProgressResumeSmall, AllIcons.Process.ProgressResume, AllIcons.Process.ProgressResumeSmallHover,
               AllIcons.Process.ProgressResumeHover);
    }

    private void setIcons(InplaceButton button, Icon compactRegular, Icon regular, Icon compactHovered, Icon hovered) {
      button.setIcons(isCompact() ? compactRegular : regular, null, isCompact() ? compactHovered : hovered);
      button.revalidate();
      button.repaint();
    }

    protected @Nullable ProgressSuspender getSuspender() {
      ProgressIndicatorEx original = myOriginal;
      return original == null ? null : ProgressSuspender.getSuspender(original);
    }

    @Override
    public void stop() {
      super.stop();
      updateProgress();
    }

    @Override
    protected boolean isFinished() {
      TaskInfo info = getInfo();
      return info == null || isFinished(info);
    }

    @Override
    public void finish(@NotNull TaskInfo task) {
      super.finish(task);
      queueRunningUpdate(() -> removeProgress(this));
    }

    @Override
    public void dispose() {
      super.dispose();
      myOriginal = null;
    }

    @Override
    protected void cancelRequest() {
      myOriginal.cancel();
    }

    @Override
    protected void queueProgressUpdate() {
      synchronized (myDirtyIndicators) {
        myDirtyIndicators.add(this);
      }
      myUpdateQueue.queue(myUpdateIndicators);
    }

    @Override
    protected void queueRunningUpdate(@NotNull Runnable update) {
      myUpdateQueue.queue(new Update(new Object(), false, 0) {
        @Override
        public void run() {
          ApplicationManager.getApplication().invokeLater(update);
        }
      });
    }

    @Override
    public void updateProgressNow() {
      myProgress.setVisible(!PowerSaveMode.isEnabled() || !isPaintingIndeterminate());
      super.updateProgressNow();
      if (myPresentationModeProgressPanel != null) myPresentationModeProgressPanel.update();
    }

    public boolean showInPresentationMode() {
      return !isProcessWindowOpen();
    }
  }

  private void runQuery() {
    if (getRootPane() == null) return;

    Set<InlineProgressIndicator> indicators = getCurrentInlineIndicators();
    if (indicators.isEmpty()) return;

    for (InlineProgressIndicator each : indicators) {
      each.updateProgress();
    }
    myQueryAlarm.cancelAllRequests();
    myQueryAlarm.addRequest(this::runQuery, 2000);
  }

  private @NotNull Set<InlineProgressIndicator> getCurrentInlineIndicators() {
    synchronized (myOriginals) {
      return myInlineToOriginal.keySet();
    }
  }

  private final class InlineProgressPanel extends NonOpaquePanel {
    private MyInlineProgressIndicator myIndicator;
    private AsyncProcessIcon myProcessIconComponent;
    private final ActionLink myMultiProcessLink = new ActionLink("", e -> { triggerPopupShowing(); }) {
      @Override
      public void updateUI() {
        super.updateUI();
        if (!ExperimentalUI.isNewUI()) {
          setFont(SystemInfo.isMac ? JBUI.Fonts.label(11) : JBFont.label());
        }
      }
    };

    InlineProgressPanel() {
      setLayout(new AbstractLayoutManager() {
        @Override
        public Dimension preferredLayoutSize(Container parent) {
          Dimension result = new Dimension();
          if (myIndicator != null) {
            JComponent component = myIndicator.getComponent();
            if (component.isVisible()) {
              Dimension size = component.getPreferredSize();
              result.width += size.width;
              result.height = Math.max(result.height, size.height);
            }
          }
          if (myMultiProcessLink.isVisible()) {
            Dimension size = myMultiProcessLink.getPreferredSize();
            result.width += (result.width > 0 ? getGap() : 0) + size.width;
            result.height = Math.max(result.height, size.height);
          }
          if (myProcessIconComponent != null) {
            result.height = Math.max(result.height, myProcessIconComponent.getPreferredSize().height);
          }
          JBInsets.addTo(result, parent.getInsets());
          return result;
        }

        @Override
        public void layoutContainer(Container parent) {
          if (myIndicator == null) {
            hideProcessIcon();
            return;
          }

          Insets insets = parent.getInsets();
          int x = insets.left;
          int centerY = (parent.getHeight() + insets.top - insets.bottom) / 2;
          int width = parent.getWidth() - insets.left - insets.right;
          int rightX = parent.getWidth() - insets.right;
          int gap = getGap();

          JComponent indicator = myIndicator.getComponent();

          if (indicator.isVisible()) {
            int preferredWidth = preferredLayoutSize(parent).width - insets.left - insets.right;
            Dimension indicatorSize = null;

            if (preferredWidth > width) {
              int progressWidth2x = myIndicator.myProgress.getPreferredSize().width * 2;
              if (width > progressWidth2x && myIndicator.myText.getPreferredSize().width > progressWidth2x) {
                preferredWidth = width;
                indicatorSize = new Dimension(width, indicator.getPreferredSize().height);
                if (myMultiProcessLink.isVisible()) {
                  indicatorSize.width -= myMultiProcessLink.getPreferredSize().width + gap;
                }
              }
            }

            if (preferredWidth > width) {
              indicator.setBounds(0, 0, 0, 0);

              addProcessIcon();

              Dimension iconSize = myProcessIconComponent.getPreferredSize();
              preferredWidth = iconSize.width;

              if (myMultiProcessLink.isVisible()) {
                preferredWidth += gap + myMultiProcessLink.getPreferredSize().width;
              }

              if (preferredWidth > width) {
                if (myMultiProcessLink.isVisible()) {
                  myMultiProcessLink.setBounds(0, 0, 0, 0);
                }

                setBounds(myProcessIconComponent, 0, centerY, iconSize, false);
              }
              else {
                boolean minisWidth = true;

                if (myMultiProcessLink.isVisible()) {
                  rightX = setBounds(myMultiProcessLink, rightX, centerY, null, true) - gap;
                }
                else if (width < 60) {
                  rightX = 0;
                  minisWidth = false;
                }

                setBounds(myProcessIconComponent, rightX, centerY, iconSize, minisWidth);
              }

              myProcessIconComponent.setVisible(true);
            }
            else {
              hideProcessIcon();

              if (myMultiProcessLink.isVisible()) {
                rightX = setBounds(myMultiProcessLink, rightX, centerY, null, true) - gap;
              }

              setBounds(indicator, rightX, centerY, indicatorSize, true);
            }
          }
          else {
            Dimension linkSize = myMultiProcessLink.getPreferredSize();
            int preferredWidth = linkSize.width;

            if (preferredWidth > width) {
              myMultiProcessLink.setBounds(0, 0, 0, 0);

              addProcessIcon();
              setBounds(myProcessIconComponent, x, centerY, null, false);
              myProcessIconComponent.setVisible(true);
            }
            else {
              hideProcessIcon();

              setBounds(myMultiProcessLink, rightX, centerY, linkSize, true);
            }
          }
        }
      });
      setBorder(JBUI.Borders.empty(0, 20, 0, 4));

      add(myMultiProcessLink);
      myMultiProcessLink.setVisible(false);
    }

    private int getGap() {
      return JBUI.scale(10);
    }

    private int setBounds(@NotNull JComponent component, int x, int centerY, @Nullable Dimension size, boolean minusWidth) {
      if (size == null) {
        size = component.getPreferredSize();
      }
      if (minusWidth) {
        x -= size.width;
      }
      component.setBounds(x, centerY - size.height / 2, size.width, size.height);
      return x;
    }

    private void addProcessIcon() {
      if (myProcessIconComponent == null) {
        add(myProcessIconComponent = myProgressIcon.getValue());
      }
    }

    private void hideProcessIcon() {
      if (myProcessIconComponent != null) {
        myProcessIconComponent.setVisible(false);
      }
    }

    void updateState(@Nullable MyInlineProgressIndicator indicator) {
      if (getRootPane() == null) {
        return; // e.g. project frame is closed
      }
      if (myIndicator != null) {
        remove(myIndicator.getComponent());
      }

      myIndicator = indicator;

      if (indicator == null) {
        myMultiProcessLink.setVisible(false);
        doLayout();
        revalidate();
        repaint();
      }
      else {
        add(indicator.getComponent());
        updateState();
      }
    }

    void updateState() {
      updateState(myPopup.isShowing());
    }

    void updateState(boolean showPopup) {
      if (myIndicator == null) {
        return;
      }

      int size = myOriginals.size();

      myIndicator.getComponent().setVisible(!showPopup);
      myMultiProcessLink.setVisible(showPopup || size > 1);

      if (showPopup) {
        myMultiProcessLink.setText(IdeBundle.message("link.hide.processes", size));
      }
      else if (size > 1) {
        myMultiProcessLink.setText(IdeBundle.message("link.show.all.processes", size));
      }

      doLayout();
      revalidate();
      repaint();
    }
  }

  private static class InlineLayout extends AbstractLayoutManager {
    @Override
    public Dimension preferredLayoutSize(Container parent) {
      Dimension result = new Dimension();
      int count = parent.getComponentCount();
      for (int i = 0; i < count; i++) {
        Dimension size = parent.getComponent(i).getPreferredSize();
        result.width += size.width;
        result.height = Math.max(result.height, size.height);
      }
      return result;
    }

    @Override
    public void layoutContainer(Container parent) {
      if (parent.getComponentCount() != 2) {
        return; // e.g. project frame is closed
      }

      Component infoPanel = parent.getComponent(0);
      Component progressPanel = parent.getComponent(1);
      Dimension size = parent.getSize();
      int progressWidth = progressPanel.getPreferredSize().width;

      if (progressWidth > size.width) {
        infoPanel.setBounds(0, 0, 0, 0);
        progressPanel.setBounds(0, 0, size.width, size.height);
      }
      else {
        infoPanel.setBounds(0, 0, size.width - progressWidth, size.height);
        progressPanel.setBounds(size.width - progressWidth, 0, progressWidth, size.height);
      }
    }
  }
}
