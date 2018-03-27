/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.wm.impl.status;

import com.intellij.icons.AllIcons;
import com.intellij.ide.PowerSaveMode;
import com.intellij.idea.ActionsBundle;
import com.intellij.internal.statistic.customUsageCollectors.actions.ActionsCollector;
import com.intellij.notification.EventLog;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.impl.ToolWindowsPane;
import com.intellij.ui.BalloonLayoutImpl;
import com.intellij.ui.Gray;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.*;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

import static com.intellij.icons.AllIcons.Process.*;

public class InfoAndProgressPanel extends JPanel implements CustomStatusBarWidget {
  private final ProcessPopup myPopup;

  private final StatusPanel myInfoPanel = new StatusPanel();
  private final JPanel myRefreshAndInfoPanel = new JPanel();
  private final AnimatedIcon myProgressIcon;

  private final List<ProgressIndicatorEx> myOriginals = new ArrayList<>();
  private final List<TaskInfo> myInfos = new ArrayList<>();
  private final Map<InlineProgressIndicator, ProgressIndicatorEx> myInline2Original = new HashMap<>();
  private final MultiValuesMap<ProgressIndicatorEx, MyInlineProgressIndicator> myOriginal2Inlines = new MultiValuesMap<>();

  private final MergingUpdateQueue myUpdateQueue;
  private final Alarm myQueryAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private boolean myShouldClosePopupAndOnProcessFinish;

  private final Alarm myRefreshAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final AnimatedIcon myRefreshIcon;

  private String myCurrentRequestor;
  private boolean myDisposed;

  private final Set<InlineProgressIndicator> myDirtyIndicators = ContainerUtil.newIdentityTroveSet();
  private final Update myUpdateIndicators = new Update("UpdateIndicators", false, 1) {
    @Override
    public void run() {
      List<InlineProgressIndicator> indicators;
      synchronized (myDirtyIndicators) {
        indicators = ContainerUtil.newArrayList(myDirtyIndicators);
        myDirtyIndicators.clear();
      }
      for (InlineProgressIndicator indicator : indicators) {
        indicator.updateAndRepaint();
      }
    }
  };

  InfoAndProgressPanel() {

    setOpaque(false);

    myRefreshIcon = new RefreshFileSystemIcon();
    myRefreshIcon.setPaintPassiveIcon(false);

    myRefreshAndInfoPanel.setLayout(new BorderLayout());
    myRefreshAndInfoPanel.setOpaque(false);
    myRefreshAndInfoPanel.add(myRefreshIcon, BorderLayout.WEST);
    myRefreshAndInfoPanel.add(myInfoPanel, BorderLayout.CENTER);

    myProgressIcon = new AsyncProcessIcon("Background process");
    myProgressIcon.setOpaque(false);

    myProgressIcon.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        handle(e);
      }
      @Override
      public void mouseReleased(MouseEvent e) {
        handle(e);
      }
    });

    myProgressIcon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myProgressIcon.setBorder(StatusBarWidget.WidgetBorder.INSTANCE);
    myProgressIcon.setToolTipText(ActionsBundle.message("action.ShowProcessWindow.double.click"));

    myUpdateQueue = new MergingUpdateQueue("Progress indicator", 50, true, MergingUpdateQueue.ANY_COMPONENT);
    myPopup = new ProcessPopup(this);

    setRefreshVisible(false);

    restoreEmptyStatus();

    runOnPowerSaveChange(this::updateProgressIcon, this);
  }

  private void runOnPowerSaveChange(@NotNull Runnable runnable, Disposable parentDisposable) {
    synchronized (myOriginals) {
      if (!myDisposed) {
        ApplicationManager.getApplication().getMessageBus().connect(parentDisposable)
          .subscribe(PowerSaveMode.TOPIC, () -> UIUtil.invokeLaterIfNeeded(runnable));
      }
    }
  }

  private void handle(MouseEvent e) {
    if (UIUtil.isActionClick(e, MouseEvent.MOUSE_PRESSED)) {
      if (!myPopup.isShowing()) {
        openProcessPopup(true);
      } else {
        hideProcessPopup();
      }
    } else if (e.isPopupTrigger()) {
      ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("BackgroundTasks");
      ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group).getComponent().show(e.getComponent(), e.getX(), e.getY());
    }
  }

  @Override
  @NotNull
  public String ID() {
    return "InfoAndProgress";
  }

  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
  }

  @Override
  public void dispose() {
    setRefreshVisible(false);
    synchronized (myOriginals) {
      restoreEmptyStatus();
      for (InlineProgressIndicator indicator : myInline2Original.keySet()) {
        Disposer.dispose(indicator);
      }
      myInline2Original.clear();
      myOriginal2Inlines.clear();

      myDisposed = true;
    }
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
    synchronized (myOriginals) {
      final boolean veryFirst = !hasProgressIndicators();

      myOriginals.add(original);
      myInfos.add(info);

      MyInlineProgressIndicator expanded = createInlineDelegate(info, original, false);
      MyInlineProgressIndicator compact = createInlineDelegate(info, original, true);

      myPopup.addIndicator(expanded);
      updateProgressIcon();

      if (veryFirst && !myPopup.isShowing()) {
        buildInInlineIndicator(compact);
      }
      else {
        buildInProcessCount();
        if (myInfos.size() > 1 && Registry.is("ide.windowSystem.autoShowProcessPopup")) {
          openProcessPopup(false);
        }
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
      if (!myInline2Original.containsKey(progress)) return; // already disposed

      final boolean last = myOriginals.size() == 1;
      final boolean beforeLast = myOriginals.size() == 2;

      myPopup.removeIndicator(progress);

      final ProgressIndicatorEx original = removeFromMaps(progress);
      if (myOriginals.contains(original)) {
        Disposer.dispose(progress);
        return;
      }

      if (last) {
        restoreEmptyStatus();
        if (myShouldClosePopupAndOnProcessFinish) {
          hideProcessPopup();
        }
      }
      else {
        if (myPopup.isShowing() || myOriginals.size() > 1) {
          buildInProcessCount();
        }
        else if (beforeLast) {
          buildInInlineIndicator(createInlineDelegate(myInfos.get(0), myOriginals.get(0), true));
        }
        else {
          restoreEmptyStatus();
        }
      }

      runQuery();
    }
    Disposer.dispose(progress);

  }

  private ProgressIndicatorEx removeFromMaps(@NotNull MyInlineProgressIndicator progress) {
    final ProgressIndicatorEx original = myInline2Original.get(progress);

    myInline2Original.remove(progress);
    synchronized (myDirtyIndicators) {
      myDirtyIndicators.remove(progress);
    }

    myOriginal2Inlines.remove(original, progress);
    if (myOriginal2Inlines.get(original) == null) {
      final int originalIndex = myOriginals.indexOf(original);
      myOriginals.remove(originalIndex);
      myInfos.remove(originalIndex);
    }

    return original;
  }

  private void openProcessPopup(boolean requestFocus) {
    synchronized (myOriginals) {
      if (myPopup.isShowing()) return;
      if (hasProgressIndicators()) {
        myShouldClosePopupAndOnProcessFinish = true;
        buildInProcessCount();
      }
      else {
        myShouldClosePopupAndOnProcessFinish = false;
        restoreEmptyStatus();
      }
      myPopup.show(requestFocus);
    }
  }

  void hideProcessPopup() {
    synchronized (myOriginals) {
      if (!myPopup.isShowing()) return;

      if (myOriginals.size() == 1) {
        buildInInlineIndicator(createInlineDelegate(myInfos.get(0), myOriginals.get(0), true));
      }
      else if (!hasProgressIndicators()) {
        restoreEmptyStatus();
      }
      else {
        buildInProcessCount();
      }

      myPopup.hide();
    }
  }

  private void buildInProcessCount() {
    removeAll();
    setLayout(new BorderLayout());

    final JPanel progressCountPanel = new JPanel(new BorderLayout(0, 0));
    progressCountPanel.setOpaque(false);
    String processWord = myOriginals.size() == 1 ? " process" : " processes";
    final LinkLabel<Object> label = new LinkLabel<>(myOriginals.size() + processWord + " running...", null,
                                                    (aSource, aLinkData) -> triggerPopupShowing());

    if (SystemInfo.isMac) label.setFont(JBUI.Fonts.label(11));

    label.setOpaque(false);

    final Wrapper labelComp = new Wrapper(label);
    labelComp.setOpaque(false);
    progressCountPanel.add(labelComp, BorderLayout.CENTER);

    //myProgressIcon.setBorder(new IdeStatusBarImpl.MacStatusBarWidgetBorder());
    progressCountPanel.add(myProgressIcon, BorderLayout.WEST);

    add(myRefreshAndInfoPanel, BorderLayout.CENTER);

    progressCountPanel.setBorder(JBUI.Borders.emptyRight(4));
    add(progressCountPanel, BorderLayout.EAST);

    revalidate();
    repaint();
  }

  private void buildInInlineIndicator(@NotNull MyInlineProgressIndicator inline) {
    removeAll();
    setLayout(new InlineLayout());
    final JRootPane pane = getRootPane();
    if (pane == null) return; // e.g. project frame is closed
    add(myRefreshAndInfoPanel);

    final JPanel inlinePanel = new JPanel(new BorderLayout());

    inline.getComponent().setBorder(JBUI.Borders.empty(1, 0, 0, 2));
    final JComponent inlineComponent = inline.getComponent();
    inlineComponent.setOpaque(false);
    inlinePanel.add(inlineComponent, BorderLayout.CENTER);

    //myProgressIcon.setBorder(new IdeStatusBarImpl.MacStatusBarWidgetBorder());
    inlinePanel.add(myProgressIcon, BorderLayout.WEST);

    inline.updateProgressNow();
    inlinePanel.setOpaque(false);

    add(inlinePanel);

    myRefreshAndInfoPanel.revalidate();
    myRefreshAndInfoPanel.repaint();

    if (inline.myPresentationModeProgressPanel != null) return;

    inline.myPresentationModeProgressPanel = new PresentationModeProgressPanel(inline);

    Component anchor = getAnchor(pane);
    final BalloonLayoutImpl balloonLayout = getBalloonLayout(pane);

    Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(inline.myPresentationModeProgressPanel.getProgressPanel())
      .setFadeoutTime(0)
      .setFillColor(Gray.TRANSPARENT)
      .setShowCallout(false)
      .setBorderColor(Gray.TRANSPARENT)
      .setBorderInsets(JBUI.emptyInsets())
      .setAnimationCycle(0)
      .setCloseButtonEnabled(false)
      .setHideOnClickOutside(false)
      .setDisposable(inline)
      .setHideOnFrameResize(false)
      .setHideOnKeyOutside(false)
      .setBlockClicksThroughBalloon(true)
      .setHideOnAction(false)
      .createBalloon();
    if (balloonLayout != null) {
      class MyListener implements JBPopupListener, Runnable {
        @Override
        public void beforeShown(LightweightWindowEvent event) {
          balloonLayout.addListener(this);
        }

        @Override
        public void onClosed(LightweightWindowEvent event) {
          balloonLayout.removeListener(this);
        }

        @Override
        public void run() {
          if (!balloon.isDisposed()) {
            balloon.revalidate();
          }
        }
      }
      balloon.addListener(new MyListener());
    }
    balloon.show(new PositionTracker<Balloon>(anchor) {
      @Override
      public RelativePoint recalculateLocation(Balloon object) {
        Component c = getAnchor(pane);
        int y = c.getHeight() - 45;
        if (balloonLayout != null && !isBottomSideToolWindowsVisible(pane)) {
          Component component = balloonLayout.getTopBalloonComponent();
          if (component != null) {
            y = SwingUtilities.convertPoint(component, 0, -45, c).y;
          }
        }

        return new RelativePoint(c, new Point(c.getWidth() - 150, y));
      }
    }, Balloon.Position.above);
  }

  @Nullable
  private static BalloonLayoutImpl getBalloonLayout(@NotNull JRootPane pane) {
    Component parent = UIUtil.findUltimateParent(pane);
    if (parent instanceof IdeFrame) {
      return (BalloonLayoutImpl)((IdeFrame)parent).getBalloonLayout();
    }
    return null;
  }

  @NotNull
  private static Component getAnchor(@NotNull JRootPane pane) {
    Component tabWrapper = UIUtil.findComponentOfType(pane, TabbedPaneWrapper.TabWrapper.class);
    if (tabWrapper != null && tabWrapper.isShowing()) return tabWrapper;
    EditorsSplitters splitters = UIUtil.findComponentOfType(pane, EditorsSplitters.class);
    if (splitters != null) {
      return splitters.isShowing() ? splitters : pane;
    }
    FileEditorManagerEx ex = FileEditorManagerEx.getInstanceEx(ProjectUtil.guessCurrentProject(pane));
    if (ex == null) return pane;
    splitters = ex.getSplitters();
    return splitters.isShowing() ? splitters : pane;
  }

  private static boolean isBottomSideToolWindowsVisible(@NotNull JRootPane parent) {
    ToolWindowsPane pane = UIUtil.findComponentOfType(parent, ToolWindowsPane.class);
    return pane != null && pane.isBottomSideToolWindowsVisible();
  }

  public Couple<String> setText(@Nullable final String text, @Nullable final String requestor) {
    if (StringUtil.isEmpty(text) && !Comparing.equal(requestor, myCurrentRequestor) && !EventLog.LOG_REQUESTOR.equals(requestor)) {
      return Couple.of(myInfoPanel.getText(), myCurrentRequestor);
    }

    boolean logMode = myInfoPanel.updateText(EventLog.LOG_REQUESTOR.equals(requestor) ? "" : text);
    myCurrentRequestor = logMode ? EventLog.LOG_REQUESTOR : requestor;
    return Couple.of(text, requestor);
  }

  void setRefreshVisible(final boolean visible) {
    UIUtil.invokeLaterIfNeeded(() -> {
      myRefreshAlarm.cancelAllRequests();
      myRefreshAlarm.addRequest(() -> {
        if (visible) {
          myRefreshIcon.resume();
        }
        else {
          myRefreshIcon.suspend();
        }
        myRefreshIcon.revalidate();
        myRefreshIcon.repaint();
      }, visible ? 100 : 300);
    });
  }

  void setRefreshToolTipText(final String tooltip) {
    myRefreshIcon.setToolTipText(tooltip);
  }

  public BalloonHandler notifyByBalloon(MessageType type, String htmlBody, @Nullable Icon icon, @Nullable HyperlinkListener listener) {
    final Balloon balloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
      htmlBody.replace("\n", "<br>"),
      icon != null ? icon : type.getDefaultIcon(),
      type.getPopupBackground(),
      listener).createBalloon();

    SwingUtilities.invokeLater(() -> {
      Component comp = this;
      if (comp.isShowing()) {
        int offset = comp.getHeight() / 2;
        Point point = new Point(comp.getWidth() - offset, comp.getHeight() - offset);
        balloon.show(new RelativePoint(comp, point), Balloon.Position.above);
      } else {
        final JRootPane rootPane = SwingUtilities.getRootPane(comp);
        if (rootPane != null && rootPane.isShowing()) {
          final Container contentPane = rootPane.getContentPane();
          final Rectangle bounds = contentPane.getBounds();
          final Point target = UIUtil.getCenterPoint(bounds, JBUI.size(1, 1));
          target.y = bounds.height - 3;
          balloon.show(new RelativePoint(contentPane, target), Balloon.Position.above);
        }
      }
    });

    return () -> SwingUtilities.invokeLater(balloon::hide);
  }

  private static class InlineLayout extends AbstractLayoutManager {
    private int myProgressWidth;

    @Override
    public Dimension preferredLayoutSize(final Container parent) {
      Dimension result = new Dimension();
      for (int i = 0; i < parent.getComponentCount(); i++) {
        final Dimension prefSize = parent.getComponent(i).getPreferredSize();
        result.width += prefSize.width;
        result.height = Math.max(prefSize.height, result.height);
      }
      return result;
    }

    @Override
    public void layoutContainer(final Container parent) {
      assert parent.getComponentCount() == 2; // 1. info; 2. progress

      Component infoPanel = parent.getComponent(0);
      Component progressPanel = parent.getComponent(1);
      int progressPrefWidth = progressPanel.getPreferredSize().width;

      final Dimension size = parent.getSize();
      int maxProgressWidth = (int) (size.width * 0.8);
      int minProgressWidth = (int) (size.width * 0.5);
      if (progressPrefWidth > myProgressWidth) {
        myProgressWidth = progressPrefWidth;
      }
      if (myProgressWidth > maxProgressWidth) {
        myProgressWidth = maxProgressWidth;
      }
      if (myProgressWidth < minProgressWidth) {
        myProgressWidth = minProgressWidth;
      }
      infoPanel.setBounds(0, 0, size.width - myProgressWidth, size.height);
      progressPanel.setBounds(size.width - myProgressWidth, 0, myProgressWidth, size.height);
    }
  }

  @NotNull
  private MyInlineProgressIndicator createInlineDelegate(@NotNull TaskInfo info, @NotNull ProgressIndicatorEx original, boolean compact) {
    Collection<MyInlineProgressIndicator> inlines = myOriginal2Inlines.get(original);
    if (inlines != null) {
      for (MyInlineProgressIndicator eachInline : inlines) {
        if (eachInline.isCompact() == compact) return eachInline;
      }
    }

    MyInlineProgressIndicator inline = new MyInlineProgressIndicator(compact, info, original);

    myInline2Original.put(inline, original);
    myOriginal2Inlines.put(original, inline);

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
      openProcessPopup(true);
    }
  }

  private void restoreEmptyStatusInner() {
    removeAll();
    updateProgressIcon();
    Container iconParent = myProgressIcon.getParent();
    if (iconParent != null) {
      iconParent.remove(myProgressIcon); // to prevent leaks to this removed parent via progress icon
    }
  }

  private void updateProgressIcon() {
    if (myOriginals.isEmpty() || PowerSaveMode.isEnabled() ||
        myOriginals.stream().map(ProgressSuspender::getSuspender).filter(Objects::nonNull).anyMatch(ProgressSuspender::isSuspended)) {
      myProgressIcon.suspend();
    } else {
      myProgressIcon.resume();
    }
  }

  private void restoreEmptyStatus() {
    restoreEmptyStatusInner();
    setLayout(new BorderLayout());
    add(myRefreshAndInfoPanel, BorderLayout.CENTER);

    myRefreshAndInfoPanel.revalidate();
    myRefreshAndInfoPanel.repaint();
  }

  //private String formatTime(long t) {
  //  if (t < 1000) return "< 1 sec";
  //  if (t < 60 * 1000) return (t / 1000) + " sec";
  //  return "~" + (int)Math.ceil(t / (60 * 1000f)) + " min";
  //}

  boolean isProcessWindowOpen() {
    return myPopup.isShowing();
  }

  void setProcessWindowOpen(final boolean open) {
    if (open) {
      openProcessPopup(true);
    }
    else {
      hideProcessPopup();
    }
  }

  private class MyInlineProgressIndicator extends InlineProgressIndicator {
    private ProgressIndicatorEx myOriginal;
    private PresentationModeProgressPanel myPresentationModeProgressPanel;

    MyInlineProgressIndicator(final boolean compact, @NotNull TaskInfo task, @NotNull ProgressIndicatorEx original) {
      super(compact, task);
      myOriginal = original;
      original.addStateDelegate(this);
      addStateDelegate(new AbstractProgressIndicatorExBase(){
        @Override
        public void cancel() {
          super.cancel();
          updateProgress();
        }
      });
      runOnPowerSaveChange(this::queueProgressUpdate, this);
    }

    @Override
    public String getText() {
      String text = StringUtil.notNullize(super.getText());
      ProgressSuspender suspender = getSuspender();
      return suspender != null && suspender.isSuspended() ? suspender.getSuspendedText() : text;
    }

    @Override
    protected JBIterable<ProgressButton> createEastButtons() {
      return JBIterable.of(createSuspendButton()).append(super.createEastButtons());
    }

    private ProgressButton createSuspendButton() {
      InplaceButton suspendButton = new InplaceButton("", AllIcons.Actions.Pause, e -> {
        ProgressSuspender suspender = Objects.requireNonNull(getSuspender());
        suspender.setSuspended(!suspender.isSuspended());
        updateProgressNow();
        ActionsCollector.getInstance().record(suspender.isSuspended() ? "Progress Paused" : "Progress Resumed");
      }).setFillBg(false);
      suspendButton.setVisible(false);

      return new ProgressButton(suspendButton, () -> {
        ProgressSuspender suspender = getSuspender();
        suspendButton.setVisible(suspender != null);
        if (suspender != null) {
          String toolTipText = suspender.isSuspended() ? "Resume" : "Pause";
          if (!toolTipText.equals(suspendButton.getToolTipText())) {
            updateProgressIcon();
            if (suspender.isSuspended()) showResumeIcons(suspendButton);
            else showPauseIcons(suspendButton);
            suspendButton.setToolTipText(toolTipText);
          }
        }
      });
    }

    private void showPauseIcons(InplaceButton button) {
      setIcons(button, ProgressPauseSmall, ProgressPause, ProgressPauseSmallHover, ProgressPauseHover);
    }
    private void showResumeIcons(InplaceButton button) {
      setIcons(button, ProgressResumeSmall, ProgressResume, ProgressResumeSmallHover, ProgressResumeHover);
    }

    private void setIcons(InplaceButton button, Icon compactRegular, Icon regular, Icon compactHovered, Icon hovered) {
      button.setIcons(isCompact() ? compactRegular : regular, null, isCompact() ? compactHovered : hovered);
    }

    @Nullable
    private ProgressSuspender getSuspender() {
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
    public void finish(@NotNull final TaskInfo task) {
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
    protected void queueRunningUpdate(@NotNull final Runnable update) {
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

  @NotNull
  private Set<InlineProgressIndicator> getCurrentInlineIndicators() {
    synchronized (myOriginals) {
      return myInline2Original.keySet();
    }
  }
}
