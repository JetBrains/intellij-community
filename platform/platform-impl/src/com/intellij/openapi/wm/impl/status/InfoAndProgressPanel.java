// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.icons.AllIcons;
import com.intellij.ide.PowerSaveMode;
import com.intellij.idea.ActionsBundle;
import com.intellij.internal.statistic.service.fus.collectors.UIEventId;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.notification.EventLog;
import com.intellij.openapi.Disposable;
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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts.PopupContent;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.impl.ToolWindowsPane;
import com.intellij.reference.SoftReference;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.messages.MessageBusConnection;
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
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.*;

public final class InfoAndProgressPanel extends JPanel implements CustomStatusBarWidget {
  private final ProcessPopup myPopup;

  private final StatusPanel myInfoPanel = new StatusPanel();
  private final JPanel myRefreshAndInfoPanel = new JPanel();
  private final NotNullLazyValue<AsyncProcessIcon> myProgressIcon = new NotNullLazyValue<AsyncProcessIcon>() {
    @Override
    protected @NotNull AsyncProcessIcon compute() {
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
      icon.setBorder(StatusBarWidget.WidgetBorder.INSTANCE);
      icon.setToolTipText(ActionsBundle.message("action.ShowProcessWindow.double.click"));
      return icon;
    }
  };

  private final List<ProgressIndicatorEx> myOriginals = new ArrayList<>();
  private final List<TaskInfo> myInfos = new ArrayList<>();
  private final Map<InlineProgressIndicator, ProgressIndicatorEx> myInlineToOriginal = new HashMap<>();
  private final Map<ProgressIndicatorEx, Set<MyInlineProgressIndicator>> myOriginalToInlines = new HashMap<>();

  private final MergingUpdateQueue myUpdateQueue;
  private final Alarm myQueryAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private boolean myShouldClosePopupAndOnProcessFinish;

  private final JLabel myRefreshIcon = new JLabel(new AnimatedIcon.FS());

  private String myCurrentRequestor;
  private boolean myDisposed;
  private WeakReference<Balloon> myLastShownBalloon;

  private final Set<InlineProgressIndicator> myDirtyIndicators = ContainerUtil.newIdentityTroveSet();
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
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") private LinkLabel<Object> myMultiProcessLink;

  InfoAndProgressPanel() {
    setOpaque(false);
    setBorder(JBUI.Borders.empty());

    myRefreshIcon.setVisible(false);

    myRefreshAndInfoPanel.setLayout(new BorderLayout());
    myRefreshAndInfoPanel.setOpaque(false);
    myRefreshAndInfoPanel.add(myRefreshIcon, BorderLayout.WEST);
    myRefreshAndInfoPanel.add(myInfoPanel, BorderLayout.CENTER);

    myUpdateQueue = new MergingUpdateQueue("Progress indicator", 50, true, MergingUpdateQueue.ANY_COMPONENT);
    myPopup = new ProcessPopup(this);

    setRefreshVisible(false);

    restoreEmptyStatus();

    runOnProgressRelatedChange(this::updateProgressIcon, this);
  }

  private void runOnProgressRelatedChange(@NotNull Runnable runnable, Disposable parentDisposable) {
    synchronized (myOriginals) {
      if (!myDisposed) {
        MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(parentDisposable);
        connection.subscribe(PowerSaveMode.TOPIC, () -> UIUtil.invokeLaterIfNeeded(runnable));
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

  private void handle(MouseEvent e) {
    if (UIUtil.isActionClick(e, MouseEvent.MOUSE_PRESSED)) {
      if (!myPopup.isShowing()) {
        openProcessPopup(true);
      } else {
        hideProcessPopup();
      }
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
      if (!myInlineToOriginal.containsKey(progress)) return; // already disposed

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
    final ProgressIndicatorEx original = myInlineToOriginal.get(progress);

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
      if (hasProgressIndicators()) {
        myShouldClosePopupAndOnProcessFinish = true;
        buildInProcessCount();
      }
      else {
        myShouldClosePopupAndOnProcessFinish = false;
        restoreEmptyStatus();
      }
    }
  }

  void hideProcessPopup() {
    synchronized (myOriginals) {
      if (!myPopup.isShowing()) return;
      myPopup.hide();

      if (myOriginals.size() == 1) {
        buildInInlineIndicator(createInlineDelegate(myInfos.get(0), myOriginals.get(0), true));
      }
      else if (!hasProgressIndicators()) {
        restoreEmptyStatus();
      }
      else {
        buildInProcessCount();
      }
    }
  }

  private void buildInProcessCount() {
    removeAll();
    setLayout(new BorderLayout());

    final JPanel progressCountPanel = new JPanel(new BorderLayout(0, 0));
    progressCountPanel.setOpaque(false);
    myMultiProcessLink = new LinkLabel<>(getMultiProgressLinkText(), null, (aSource, aLinkData) -> triggerPopupShowing());

    if (SystemInfo.isMac) myMultiProcessLink.setFont(JBUI.Fonts.label(11));

    myMultiProcessLink.setOpaque(false);

    Wrapper labelComp = new Wrapper(myMultiProcessLink);
    labelComp.setOpaque(false);
    progressCountPanel.add(labelComp, BorderLayout.CENTER);

    //myProgressIcon.setBorder(new IdeStatusBarImpl.MacStatusBarWidgetBorder());
    progressCountPanel.add(myProgressIcon.getValue(), BorderLayout.WEST);

    add(myRefreshAndInfoPanel, BorderLayout.CENTER);

    progressCountPanel.setBorder(JBUI.Borders.emptyRight(4));
    add(progressCountPanel, BorderLayout.EAST);

    revalidate();
    repaint();
  }

  private @NotNull String getMultiProgressLinkText() {
    ProgressIndicatorEx latest = getLatestProgress();
    String latestText = latest == null ? null : latest.getText();
    if (StringUtil.isEmptyOrSpaces(latestText) || myPopup.isShowing()) {
      return myOriginals.size() + pluralizeProcess(myOriginals.size()) + " running...";
    }
    int others = myOriginals.size() - 1;
    String trimmed = latestText.length() > 55 ? latestText.substring(0, 50) + "..." : latestText;
    return trimmed + " (" + others + " more" + pluralizeProcess(others) + ")";
  }

  private static String pluralizeProcess(int count) {
    return count == 1 ? " process" : " processes";
  }

  private ProgressIndicatorEx getLatestProgress() {
    return ContainerUtil.getLastItem(myOriginals);
  }

  @Override
  public void removeAll() {
    myMultiProcessLink = null;
    super.removeAll();
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
    inlinePanel.add(myProgressIcon.getValue(), BorderLayout.WEST);

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
      .setShadow(false)
      .createBalloon();
    if (balloonLayout != null) {
      class MyListener implements JBPopupListener, Runnable {
        @Override
        public void beforeShown(@NotNull LightweightWindowEvent event) {
          balloonLayout.addListener(this);
        }

        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
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
        int y = c.getHeight() - JBUIScale.scale(45);
        if (balloonLayout != null && !isBottomSideToolWindowsVisible(pane)) {
          Component component = balloonLayout.getTopBalloonComponent();
          if (component != null) {
            y = SwingUtilities.convertPoint(component, 0, -JBUIScale.scale(45), c).y;
          }
        }

        return new RelativePoint(c, new Point(c.getWidth() - JBUIScale.scale(150), y));
      }
    }, Balloon.Position.above);
  }

  private static @Nullable BalloonLayoutImpl getBalloonLayout(@NotNull JRootPane pane) {
    Component parent = UIUtil.findUltimateParent(pane);
    if (parent instanceof IdeFrame) {
      return (BalloonLayoutImpl)((IdeFrame)parent).getBalloonLayout();
    }
    return null;
  }

  private static @NotNull Component getAnchor(@NotNull JRootPane pane) {
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

  public @NotNull Pair<String, String> setText(@Nullable String text, @Nullable String requestor) {
    if (StringUtil.isEmpty(text) && !Objects.equals(requestor, myCurrentRequestor) && !EventLog.LOG_REQUESTOR.equals(requestor)) {
      return new Pair<>(myInfoPanel.getText(), myCurrentRequestor);
    }

    boolean logMode = myInfoPanel.updateText(EventLog.LOG_REQUESTOR.equals(requestor) ? "" : text);
    myCurrentRequestor = logMode ? EventLog.LOG_REQUESTOR : requestor;
    return new Pair<>(text, requestor);
  }

  void setRefreshVisible(final boolean visible) {
    UIUtil.invokeLaterIfNeeded(() -> myRefreshIcon.setVisible(visible));
  }

  void setRefreshToolTipText(final String tooltip) {
    myRefreshIcon.setToolTipText(tooltip);
  }

  public BalloonHandler notifyByBalloon(MessageType type, @PopupContent String htmlBody, @Nullable Icon icon, @Nullable HyperlinkListener listener) {
    final Balloon balloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
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
      } else {
        final JRootPane rootPane = SwingUtilities.getRootPane(comp);
        if (rootPane != null && rootPane.isShowing()) {
          final Container contentPane = rootPane.getContentPane();
          final Rectangle bounds = contentPane.getBounds();
          final Point target = StartupUiUtil.getCenterPoint(bounds, JBUI.size(1, 1));
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
      if (parent.getComponentCount() != 2) {
        return; // e.g. project frame is closed
      }

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

  private @NotNull MyInlineProgressIndicator createInlineDelegate(@NotNull TaskInfo info, @NotNull ProgressIndicatorEx original, boolean compact) {
    Set<MyInlineProgressIndicator> inlines = myOriginalToInlines.computeIfAbsent(original, __ -> new HashSet<>());
    if (!inlines.isEmpty()) {
      for (MyInlineProgressIndicator eachInline : inlines) {
        if (eachInline.isCompact() == compact) {
          return eachInline;
        }
      }
    }

    MyInlineProgressIndicator inline = new MyInlineProgressIndicator(compact, info, original);
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
      openProcessPopup(true);
    }
  }

  private void restoreEmptyStatusInner() {
    removeAll();
    updateProgressIcon();
    AsyncProcessIcon progressIcon = myProgressIcon.isComputed() ? myProgressIcon.getValue() : null;
    Container iconParent = progressIcon == null ? null : progressIcon.getParent();
    if (iconParent != null) {
      // to prevent leaks to this removed parent via progress icon
      iconParent.remove(progressIcon);
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

  private final class MyInlineProgressIndicator extends InlineProgressIndicator {
    private ProgressIndicatorEx myOriginal;
    private PresentationModeProgressPanel myPresentationModeProgressPanel;

    MyInlineProgressIndicator(boolean compact, @NotNull TaskInfo task, @NotNull ProgressIndicatorEx original) {
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
      runOnProgressRelatedChange(this::queueProgressUpdate, this);
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
        ProgressSuspender suspender = getSuspender();
        if (suspender == null) {
          return;
        }

        if (suspender.isSuspended()) {
          suspender.resumeProcess();
        } else {
          suspender.suspendProcess(null);
        }
        UIEventLogger.logUIEvent(
          suspender.isSuspended() ? UIEventId.ProgressPaused : UIEventId.ProgressResumed
        );
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
      setIcons(button, AllIcons.Process.ProgressPauseSmall, AllIcons.Process.ProgressPause, AllIcons.Process.ProgressPauseSmallHover, AllIcons.Process.ProgressPauseHover);
    }
    private void showResumeIcons(InplaceButton button) {
      setIcons(button, AllIcons.Process.ProgressResumeSmall, AllIcons.Process.ProgressResume, AllIcons.Process.ProgressResumeSmallHover, AllIcons.Process.ProgressResumeHover);
    }

    private void setIcons(InplaceButton button, Icon compactRegular, Icon regular, Icon compactHovered, Icon hovered) {
      button.setIcons(isCompact() ? compactRegular : regular, null, isCompact() ? compactHovered : hovered);
    }

    private @Nullable ProgressSuspender getSuspender() {
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
    public void finish(final @NotNull TaskInfo task) {
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
    protected void queueRunningUpdate(final @NotNull Runnable update) {
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
      if (myOriginal == getLatestProgress() && myMultiProcessLink != null) {
        myMultiProcessLink.setText(getMultiProgressLinkText());
      }
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
}
