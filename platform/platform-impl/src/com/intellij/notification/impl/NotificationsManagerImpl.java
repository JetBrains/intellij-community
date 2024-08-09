// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl;

import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.notification.*;
import com.intellij.notification.impl.ui.NotificationsUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCloseListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeBalloonLayoutImpl;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceKt;
import com.intellij.ui.*;
import com.intellij.ui.components.GradientViewport;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.*;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.plaf.UIResource;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public final class NotificationsManagerImpl extends NotificationsManager {
  public static final Color DEFAULT_TEXT_COLOR = new JBColor(Gray._0, Gray._191);
  public static final Color FILL_COLOR = JBColor.namedColor("Notification.background", new JBColor(Gray._242, new Color(0x4E5052)));
  public static final Color BORDER_COLOR = JBColor.namedColor("Notification.borderColor", new JBColor(0xCDB2B2B2, 0xCD565A5C));
  public static final Object NOTIFICATION_BALLOON_FLAG = new Object();

  private static final Logger LOG = Logger.getInstance(NotificationsManagerImpl.class);

  private @Nullable List<Pair<Notification, @Nullable Project>> myEarlyNotifications = new ArrayList<>();
  private final IJTracer myTracer = TelemetryManager.getInstance().getTracer(NotificationScopeKt.NotificationScope);

  public NotificationsManagerImpl() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
      @Override
      public void projectClosed(@NotNull Project project) {
        if (myEarlyNotifications != null) {
          myEarlyNotifications.removeIf(pair -> project.equals(pair.second));
        }
        for (Notification notification : getNotificationsOfType(Notification.class, project)) {
          notification.hideBalloon();
        }
        TooltipController.getInstance().resetCurrent();
      }
    });
  }

  @Override
  public void expire(@NotNull Notification notification) {
    UIUtil.invokeLaterIfNeeded(() -> {
      NotificationsToolWindowFactory.Companion.expire(notification);
    });
  }

  public void expireAll() {
    NotificationsToolWindowFactory.Companion.expireAll();
  }

  @Override
  public <T extends Notification> T @NotNull [] getNotificationsOfType(@NotNull Class<T> klass, @Nullable Project project) {
    List<T> result = new ArrayList<>();
    if (project == null || !project.isDefault() && !project.isDisposed()) {
      for (Notification notification : ActionCenter.getNotifications(project)) {
        if (klass.isInstance(notification)) {
          @SuppressWarnings("unchecked") T t = (T)notification;
          result.add(t);
        }
      }
    }
    return ArrayUtil.toObjectArray(result, klass);
  }

  @Override
  public void showNotification(@NotNull Notification notification, @Nullable Project project) {
    NotificationsConfigurationImpl configuration = NotificationsConfigurationImpl.getInstanceImpl();
    NotificationSettings settings = NotificationsConfigurationImpl.getSettings(notification.getGroupId());

    if (!configuration.isRegistered(notification.getGroupId())) {
      configuration.register(notification.getGroupId(), NotificationDisplayType.BALLOON);
    }

    if (!settings.isShouldLog() && (settings.getDisplayType() == NotificationDisplayType.NONE ||
                                    notification.isShowingPopupSuppressed() ||
                                    !configuration.SHOW_BALLOONS)) {
      notification.expire();
    }

    if (configuration.SHOW_BALLOONS) {
      if (project == null) {
        ModalityUiUtil.invokeLaterIfNeeded(ModalityState.any(), ApplicationManager.getApplication().getDisposed(),
                                           () -> showNotificationWithSpan(notification, null)
        );
      }
      else if (!project.isDisposed()) {
        StartupManager.getInstance(project).runAfterOpened(() -> {
          ModalityUiUtil.invokeLaterIfNeeded(ModalityState.any(), project.getDisposed(), () -> showNotificationWithSpan(notification, project));
        });
      }
    }
  }

  @RequiresEdt
  @ApiStatus.Internal
  public void dispatchEarlyNotifications() {
    if (myEarlyNotifications != null) {
      List<Pair<Notification, @Nullable Project>> copy = myEarlyNotifications;
      myEarlyNotifications = null;
      if (LOG.isDebugEnabled()) LOG.debug("dispatching early notifications: " + copy);
      copy.forEach(early -> showNotificationWithSpan(early.first, early.second));
    }
  }

  @RequiresEdt
  private void showNotificationInner(Notification notification, @Nullable Project project) {
    if (LOG.isDebugEnabled()) LOG.debug("incoming: " + notification + ", project=" + project);

    if (myEarlyNotifications != null) {
      myEarlyNotifications.add(new Pair<>(notification, project));
      return;
    }

    String groupId = notification.getGroupId();
    NotificationSettings settings = NotificationsConfigurationImpl.getSettings(groupId);
    NotificationDisplayType type = settings.getDisplayType();
    String toolWindowId = notification.getToolWindowId();
    if (toolWindowId == null) {
      toolWindowId = NotificationsConfigurationImpl.getInstanceImpl().getToolWindowId(groupId);
    }

    if (type == NotificationDisplayType.TOOL_WINDOW &&
        (toolWindowId == null || project == null || !ToolWindowManager.getInstance(project).canShowNotification(toolWindowId))) {
      type = NotificationDisplayType.BALLOON;
    }

    if (notification.isShowingPopupSuppressed()) {
      type = NotificationDisplayType.NONE;
      if (LOG.isDebugEnabled()) LOG.debug("showing popup is suppressed for the notification");
    }

    switch (type) {
      case NONE -> {
        if (LOG.isDebugEnabled()) LOG.debug("not shown (type=NONE): " + notification);
      }
      case STICKY_BALLOON, BALLOON -> {
        Balloon balloon = notifyByBalloon(notification, type, project);
        if (balloon == null && LOG.isDebugEnabled()) LOG.debug("not shown (no balloon): " + notification);
        if (project != null && !project.isDefault() && (!settings.isShouldLog() || type == NotificationDisplayType.STICKY_BALLOON)) {
          if (balloon == null) {
            notification.expire();
          }
          else {
            balloon.addListener(new JBPopupListener() {
              @Override
              public void onClosed(@NotNull LightweightWindowEvent event) {
                if (!event.isOk()) {
                  notification.expire();
                }
              }
            });
          }
        }
      }
      case TOOL_WINDOW -> {
        MessageType messageType = notification.getType() == NotificationType.ERROR ? MessageType.ERROR :
                                  notification.getType() == NotificationType.WARNING ? MessageType.WARNING :
                                  MessageType.INFO;
        String messageBody = notification.getTitle();
        HyperlinkListener listener = null;

        String content = notification.getContent();
        if (!content.isEmpty()) {
          if (!messageBody.isEmpty()) messageBody += HtmlChunk.br();
          messageBody += content;
        }

        List<AnAction> actions = notification.getActions();
        Map<String, AnAction> actionListeners = new HashMap<>();
        if (!actions.isEmpty()) {
          messageBody += HtmlChunk.br();

          for (int index = 0; index < actions.size(); index++) {
            AnAction action = actions.get(index);
            String text = action.getTemplatePresentation().getText();
            if (text != null) {
              String linkTarget = "notification-action-" + index + "for-tool-window-" + System.identityHashCode(notification);
              actionListeners.put(linkTarget, action);
              //noinspection StringConcatenationInLoop
              messageBody += HtmlChunk.link(linkTarget, text);
              messageBody += ' ';
            }
          }
        }

        Window window = findWindowForBalloon(project);
        if (window instanceof IdeFrame) {
          BalloonLayout layout = ((IdeFrame)window).getBalloonLayout();
          if (layout != null) {
            ((BalloonLayoutImpl)layout).remove(notification);
          }
        }

        NotificationListener notificationListener = notification.getListener();
        if (notificationListener != null || !actionListeners.isEmpty()) {
          listener = new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
              if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                AnAction action = actionListeners.get(e.getDescription());
                if (action != null) {
                  Object source = e.getSource();
                  DataContext context = source instanceof Component o ? CustomizedDataContext.withSnapshot(
                    DataManager.getInstance().getDataContext(o),
                    sink -> sink.set(Notification.KEY, notification)) : null;
                  Notification.fire(notification, action, context);
                  NotificationCollector.getInstance()
                    .logNotificationActionInvoked(project, notification, action, NotificationCollector.NotificationPlace.TOOL_WINDOW);
                  return;
                }
              }

              if (notificationListener != null) {
                notificationListener.hyperlinkUpdate(notification, e);
              }
            }
          };
        }

        //noinspection SSBasedInspection
        ToolWindowManager.getInstance(Objects.requireNonNull(project))
          .notifyByBalloon(toolWindowId, messageType, messageBody, notification.getIcon(), listener);

        NotificationCollector.getInstance().logToolWindowNotificationShown(project, notification);
      }
    }
  }

  @RequiresEdt
  private void showNotificationWithSpan(Notification notification, @Nullable Project project) {
    TraceKt.use(myTracer.spanBuilder("show notification")
                  .setAttribute("project", project != null ? project.toString() : null)
                  .setAttribute("notification", notification.toString()), __ -> {
      showNotificationInner(notification, project);
      return null;
    });
  }

  private static @Nullable Balloon notifyByBalloon(Notification notification,
                                                   NotificationDisplayType displayType,
                                                   @Nullable Project project) {
    if (isDummyEnvironment()) {
      return null;
    }
    if (!notification.canShowFor(project)) {
      return null;
    }

    Window window = findWindowForBalloon(project);
    if (!(window instanceof IdeFrame)) {
      return null;
    }

    BalloonLayout layout = ((IdeFrame)window).getBalloonLayout();
    if (layout == null) {
      return null;
    }

    Ref<BalloonLayoutData> layoutDataRef = new Ref<>();
    if (project == null || project.isDefault()) {
      BalloonLayoutData layoutData = new BalloonLayoutData();
      layoutData.groupId = "";
      layoutData.welcomeScreen = layout instanceof WelcomeBalloonLayoutImpl;
      layoutData.type = notification.getType();
      layoutDataRef.set(layoutData);
    }
    else {
      BalloonLayoutData.MergeInfo mergeData = ((BalloonLayoutImpl)layout).preMerge(notification);
      if (mergeData != null) {
        BalloonLayoutData layoutData = new BalloonLayoutData();
        layoutData.mergeData = mergeData;
        layoutDataRef.set(layoutData);
      }
    }

    Disposable disposable = project != null ? project : ApplicationManager.getApplication();
    Balloon balloon = createBalloon((IdeFrame)window, notification, false, false, layoutDataRef, disposable);

    if (notification.isExpired()) {
      return null;
    }

    BalloonLayoutData layoutData = layoutDataRef.get();
    layout.add(balloon, layoutData);
    if (balloon.isDisposed()) {
      return null;
    }
    if (layoutData != null) {
      layoutData.project = project;
    }

    if (balloon instanceof BalloonImpl) {
      frameActivateBalloonListener(balloon, () -> {
        if (!balloon.isDisposed()) {
          int delay = displayType == NotificationDisplayType.STICKY_BALLOON ? 300000 : 10000;
          ((BalloonImpl)balloon).startSmartFadeoutTimer(delay);
        }
      });
    }

    NotificationCollector.getInstance().logBalloonShown(project, displayType, notification, layoutData != null && layoutData.isExpandable);

    return balloon;
  }

  public static void frameActivateBalloonListener(@NotNull Disposable parentDisposable, @NotNull Runnable callback) {
    if (ApplicationManager.getApplication().isActive()) {
      callback.run();
    }
    else {
      Disposable listenerDisposable = Disposer.newDisposable();
      Disposer.register(parentDisposable, listenerDisposable);
      ApplicationManager.getApplication().getMessageBus().connect(listenerDisposable)
        .subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
          @Override
          public void applicationActivated(@NotNull IdeFrame ideFrame) {
            Disposer.dispose(listenerDisposable);
            callback.run();
          }
        });
    }
  }

  public static @Nullable Window findWindowForBalloon(@Nullable Project project) {
    Window frame = WindowManager.getInstance().getFrame(project);
    if (frame == null && project == null) {
      frame = (Window)WelcomeFrame.getInstance();
    }
    if (frame == null && project == null) {
      frame = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      while (frame instanceof DialogWrapperDialog) {
        DialogWrapper wrapper = ((DialogWrapperDialog)frame).getDialogWrapper();
        if (wrapper == null || !wrapper.isModalProgress()) break;
        frame = frame.getOwner();
      }
    }
    if (frame == null && project == null) {
      frame = WindowManager.getInstance().findVisibleFrame();
    }
    return frame;
  }

  public static @NotNull Balloon createBalloon(@NotNull IdeFrame window,
                                               @NotNull Notification notification,
                                               boolean showCallout,
                                               boolean hideOnClickOutside,
                                               @NotNull Ref<BalloonLayoutData> layoutDataRef,
                                               @NotNull Disposable parentDisposable) {
    return createBalloon(window.getComponent(), notification, showCallout, hideOnClickOutside, layoutDataRef, parentDisposable);
  }

  public static @NotNull Balloon createBalloon(@Nullable JComponent windowComponent,
                                               @NotNull Notification notification,
                                               boolean showCallout,
                                               boolean hideOnClickOutside,
                                               @NotNull Ref<BalloonLayoutData> layoutDataRef,
                                               @NotNull Disposable parentDisposable) {
    BalloonLayoutData layoutData = layoutDataRef.isNull() ? new BalloonLayoutData() : layoutDataRef.get();
    if (layoutData.groupId == null) {
      layoutData.groupId = notification.getGroupId();
    }
    else {
      layoutData.groupId = null;
      layoutData.mergeData = null;
    }
    layoutData.id = notification.id;
    layoutData.displayId = notification.getDisplayId();
    layoutDataRef.set(layoutData);

    if (layoutData.textColor == null) {
      layoutData.textColor = JBColor.namedColor("Notification.foreground", DEFAULT_TEXT_COLOR);
    }
    if (layoutData.fillColor == null) {
      layoutData.fillColor = FILL_COLOR;
    }
    if (layoutData.borderColor == null) {
      layoutData.borderColor = BORDER_COLOR;
    }

    if (notification.isSuggestionType()) {
      layoutData.collapseType =
        notification.isImportantSuggestion() ? BalloonLayoutData.Type.ImportantSuggestion : BalloonLayoutData.Type.Suggestion;
    }
    else {
      layoutData.collapseType = BalloonLayoutData.Type.Timeline;
    }

    boolean actions = !notification.getActions().isEmpty() || notification.getContextHelpAction() != null;
    boolean showFullContent = layoutData.showFullContent || notification instanceof NotificationFullContent;

    JEditorPane text = new JEditorPane() {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (layoutData.showMinSize) {
          Point location = getCollapsedTextEndLocation(this, layoutData);
          if (location != null) {
            if (g instanceof Graphics2D) {
              ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            }
            g.setColor(getForeground());
            g.drawString("...", location.x, location.y + g.getFontMetrics().getAscent());
          }
        }
      }
    };
    NotificationsUtil.configureHtmlEditorKit(text, true);
    text.setForeground(layoutData.textColor);

    HyperlinkListener listener = NotificationsUtil.wrapListener(notification);
    if (listener != null) {
      text.addHyperlinkListener(listener);
    }

    Supplier<@Nls String> textBuilder = () -> {
      String fontStyle = NotificationsUtil.getFontStyle();
      int prefSize = new JLabel(NotificationsUtil.buildHtml(notification, null, true, null, fontStyle)).getPreferredSize().width;
      String style;

      if (layoutData.showFullContent) {
        style = prefSize > BalloonLayoutConfiguration.MaxFullContentWidth() ? BalloonLayoutConfiguration.MaxFullContentWidthStyle() : null;
      }
      else {
        style = prefSize > BalloonLayoutConfiguration.MaxWidth() ? BalloonLayoutConfiguration.MaxWidthStyle() : null;
      }

      return NotificationsUtil.buildHtml(notification, style, true, null, fontStyle);
    };

    String textContent = textBuilder.get();
    text.setText(textContent);
    setTextAccessibleName(text, textContent);
    text.setEditable(false);
    text.setOpaque(false);

    text.setBorder(null);

    JPanel content = new NonOpaquePanel(new BorderLayout(JBUI.scale(ExperimentalUI.isNewUI() ? 2 : 0), 0));
    content.setBorder(JBUI.Borders.empty(JBUI.insets("Notification.borderInsets",
                                                     ExperimentalUI.isNewUI() ? JBUI.insets(4, 4, 6, 0) : JBInsets.emptyInsets())));

    if (text.getCaret() != null) {
      text.setCaretPosition(0);
    }

    JScrollPane pane = createBalloonScrollPane(text, false);

    pane.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
      @Override
      public void adjustmentValueChanged(AdjustmentEvent e) {
        JScrollBar scrollBar = pane.getVerticalScrollBar();
        if (layoutData.showMinSize && scrollBar.getValue() > 0) {
          scrollBar.removeAdjustmentListener(this);
          scrollBar.setValue(0);
          scrollBar.addAdjustmentListener(this);
        }
      }
    });

    LinkLabel<Void> expandAction = null;

    int lines = 4;
    if (notification.hasTitle()) {
      lines--;
    }
    if (actions) {
      lines--;
    }

    layoutData.fullHeight = text.getPreferredSize().height;
    layoutData.twoLineHeight = calculateContentHeight(lines);
    layoutData.maxScrollHeight = Math.min(layoutData.fullHeight, calculateContentHeight(10));
    layoutData.configuration = BalloonLayoutConfiguration.create(notification, layoutData, actions);

    if (layoutData.welcomeScreen) {
      layoutData.maxScrollHeight = layoutData.fullHeight;
    }
    else if (!showFullContent && layoutData.maxScrollHeight != layoutData.fullHeight) {
      pane.setViewport(new GradientViewport(text, JBInsets.create(10, 0), true) {
        @Override
        protected @Nullable Color getViewColor() {
          return layoutData.fillColor;
        }

        @Override
        protected void paintGradient(Graphics g) {
          if (!layoutData.showMinSize) {
            super.paintGradient(g);
          }
        }
      });
    }

    configureBalloonScrollPane(pane, layoutData.fillColor);

    if (showFullContent) {
      if (windowComponent == null) {
        pane.setPreferredSize(text.getPreferredSize());
      }
      else {
        pane.setPreferredSize(
          new Dimension(text.getPreferredSize().width, (int)Math.min(layoutData.fullHeight, windowComponent.getHeight() * 0.75)));
      }
    }
    else if (layoutData.twoLineHeight < layoutData.fullHeight) {
      text.setPreferredSize(null);
      Dimension size = text.getPreferredSize();
      size.height = layoutData.twoLineHeight;
      text.setPreferredSize(size);
      text.setSize(size);
      layoutData.showMinSize = true;

      pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
      pane.setPreferredSize(size);

      text.setCaret(new TextCaret(layoutData));

      expandAction = new LinkLabel<>(null, AllIcons.Ide.Notification.Expand, (link, ignored) -> {
        layoutData.showMinSize = !layoutData.showMinSize;

        text.setPreferredSize(null);
        Dimension _size = text.getPreferredSize();

        if (layoutData.showMinSize) {
          _size.height = layoutData.twoLineHeight;
          pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
          link.setIcon(AllIcons.Ide.Notification.Expand);
          link.setHoveringIcon(AllIcons.Ide.Notification.ExpandHover);
          NotificationCollector.getInstance().logNotificationBalloonCollapsed(layoutData.project, notification);
        }
        else {
          text.select(0, 0);
          _size.height = layoutData.fullHeight;
          pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
          link.setIcon(AllIcons.Ide.Notification.Collapse);
          link.setHoveringIcon(AllIcons.Ide.Notification.CollapseHover);
          NotificationCollector.getInstance().logNotificationBalloonExpanded(layoutData.project, notification);
        }

        text.setPreferredSize(_size);
        text.setSize(_size);

        if (!layoutData.showMinSize) {
          _size = new Dimension(_size.width, layoutData.maxScrollHeight);
        }
        pane.setPreferredSize(_size);

        content.doLayout();
        layoutData.doLayout.run();
      });
      expandAction.setHoveringIcon(AllIcons.Ide.Notification.ExpandHover);
      layoutData.isExpandable = true;
    }

    NotificationCenterPanel centerPanel = new NotificationCenterPanel(text, layoutData);
    content.add(centerPanel, BorderLayout.CENTER);

    if (notification.hasTitle()) {
      String titleStyle = StringUtil.defaultIfEmpty(NotificationsUtil.getFontStyle(), "") + "white-space:nowrap;";
      JLabel title = new JLabel();
      String titleContent = NotificationsUtil.buildHtml(notification, titleStyle, false, null, null);
      title.setText(titleContent);
      setTextAccessibleName(title, titleContent);
      title.setOpaque(false);
      title.setForeground(layoutData.textColor);
      centerPanel.addTitle(title);
    }

    if (expandAction != null) {
      centerPanel.addExpandAction(expandAction);
    }

    if (notification.hasContent()) {
      centerPanel.addContent(layoutData.welcomeScreen ? text : pane);
    }

    Icon icon = NotificationsUtil.getIcon(notification);
    JComponent iconComponent = new JComponent() {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        icon.paintIcon(this, g, layoutData.configuration.iconOffset.width, layoutData.configuration.iconOffset.height);
      }
    };
    iconComponent.setOpaque(false);

    Runnable iconSizeRunnable = () -> iconComponent.setPreferredSize(
      new Dimension(Math.max(layoutData.configuration.iconPanelWidth, icon.getIconWidth() + layoutData.configuration.iconOffset.width + JBUIScale.scale(5)),
                    2 * layoutData.configuration.iconOffset.height + icon.getIconHeight()));
    iconSizeRunnable.run();

    content.add(iconComponent, BorderLayout.WEST);

    HoverAdapter hoverAdapter = new HoverAdapter();
    hoverAdapter.addSource(content);
    hoverAdapter.addSource(centerPanel);
    hoverAdapter.addSource(text);
    hoverAdapter.addSource(pane);

    if (actions) {
      createActionPanel(notification, centerPanel, layoutData.configuration.actionGap, hoverAdapter);
    }

    if (expandAction != null) {
      hoverAdapter.addComponent(expandAction, component -> {
        Rectangle bounds;
        Point location = SwingUtilities.convertPoint(content.getParent(), content.getLocation(), component.getParent());
        if (layoutData.showMinSize) {
          Component centerComponent = layoutData.welcomeScreen ? text : pane;
          Point centerLocation =
            SwingUtilities.convertPoint(centerComponent.getParent(), centerComponent.getLocation(), component.getParent());
          bounds = new Rectangle(location.x, centerLocation.y, content.getWidth(), centerComponent.getHeight());
        }
        else {
          bounds = new Rectangle(location.x, component.getY(), content.getWidth(), component.getHeight());
          JBInsets.addTo(bounds, JBUI.insets(5, 0, 7, 0));
        }
        return bounds;
      });
    }

    hoverAdapter.initListeners();

    if (layoutData.mergeData != null) {
      createMergeAction(layoutData, content);
    }

    text.setSize(text.getPreferredSize());

    Runnable paneSizeRunnable = () -> {
      Dimension paneSize = new Dimension(text.getPreferredSize());
      int maxWidth = JBUIScale.scale(600);
      if (windowComponent != null) {
        maxWidth = Math.min(maxWidth, windowComponent.getWidth() - 20);
      }
      if (paneSize.width > maxWidth) {
        pane.setPreferredSize(new Dimension(maxWidth, paneSize.height + UIUtil.getScrollBarWidth()));
      }
    };
    paneSizeRunnable.run();

    content.putClientProperty(NOTIFICATION_BALLOON_FLAG, new Object());

    BalloonBuilder builder = JBPopupFactory.getInstance().createBalloonBuilder(content);
    builder.setFillColor(layoutData.fillColor)
      .setCloseButtonEnabled(true)
      .setShowCallout(showCallout)
      .setShadow(false)
      .setAnimationCycle(200)
      .setHideOnClickOutside(hideOnClickOutside)
      .setHideOnAction(hideOnClickOutside)
      .setHideOnKeyOutside(hideOnClickOutside)
      .setHideOnFrameResize(false)
      .setBorderColor(layoutData.borderColor)
      .setBorderInsets(JBInsets.emptyInsets());

    Balloon balloon = builder.createBalloon();

    if (balloon instanceof BalloonImpl balloonImpl) {
      balloonImpl.getContent().addMouseListener(new MouseAdapter() {
      });
      balloon.setAnimationEnabled(false);
      balloonImpl.setShadowBorderProvider(new NotificationBalloonRoundShadowBorderProvider(layoutData.fillColor, layoutData.borderColor));

      if (!layoutData.welcomeScreen) {
        balloonImpl.setActionProvider(new NotificationBalloonActionProvider(balloonImpl, centerPanel.getTitle(), layoutData, notification));
      }

      if (layoutData.fadeoutTime != 0) {
        ((BalloonImpl)balloon).startSmartFadeoutTimer((int)layoutData.fadeoutTime);
      }
    }
    notification.setBalloon(balloon);

    int _lines = lines;
    Runnable lafCallback = () -> {
      NotificationsUtil.configureHtmlEditorKit(text, true);
      text.setText(textBuilder.get());

      text.setPreferredSize(null);
      Dimension size = text.getPreferredSize();

      layoutData.fullHeight = size.height;
      layoutData.twoLineHeight = calculateContentHeight(_lines);
      layoutData.maxScrollHeight = Math.min(layoutData.fullHeight, calculateContentHeight(10));
      layoutData.configuration = BalloonLayoutConfiguration.create(notification, layoutData, actions);

      if (layoutData.welcomeScreen) {
        layoutData.maxScrollHeight = layoutData.fullHeight;
      }

      if (showFullContent) {
        if (windowComponent == null) {
          pane.setPreferredSize(size);
        }
        else {
          pane.setPreferredSize(new Dimension(size.width, (int)Math.min(layoutData.fullHeight, windowComponent.getHeight() * 0.75)));
        }
      }
      else if (layoutData.twoLineHeight < layoutData.fullHeight) {
        size.height = layoutData.twoLineHeight;
        text.setPreferredSize(size);
        text.setSize(size);
        layoutData.showMinSize = true;

        pane.setPreferredSize(size);
      }

      text.revalidate();
      text.repaint();

      iconSizeRunnable.run();
      paneSizeRunnable.run();

      content.doLayout();

      Runnable doLayout = layoutData.doLayout;
      if (doLayout != null) {
        doLayout.run();
      }

      Container parent = content.getParent();
      if (parent == null) {
        parent = content;
      }
      parent.doLayout();
      parent.revalidate();
      parent.repaint();
    };

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(balloon);
    connection.subscribe(LafManagerListener.TOPIC, source -> {
      // We need to call this callback in the next iteration,
      // because otherwise some components inside the balloon may update their borders after all sizes are calculated inside `lafCallback`
      SwingUtilities.invokeLater(lafCallback);
    });
    connection.subscribe(UISettingsListener.TOPIC, uiSettings -> lafCallback.run());

    Disposer.register(parentDisposable, balloon);
    return balloon;
  }

  public static void setTextAccessibleName(@NotNull JComponent component, @NotNull String htmlContent) {
    component.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY,
                                StringUtil.unescapeXmlEntities(StringUtil.stripHtml(htmlContent, " ")));
  }

  public static @NotNull JScrollPane createBalloonScrollPane(@NotNull Component content, boolean configure) {
    JScrollPane pane = ScrollPaneFactory.createScrollPane(content, true);
    if (configure) {
      configureBalloonScrollPane(pane, FILL_COLOR);
    }
    return pane;
  }

  public static void configureBalloonScrollPane(@NotNull JScrollPane pane, @NotNull Color fillColor) {
    pane.setOpaque(false);
    pane.getViewport().setOpaque(false);
    pane.setBackground(fillColor);
    pane.getViewport().setBackground(fillColor);
    pane.getVerticalScrollBar().setBackground(fillColor);
  }

  private static void createActionPanel(Notification notification,
                                        NotificationCenterPanel centerPanel,
                                        int gap,
                                        HoverAdapter hoverAdapter) {
    NotificationActionPanel actionPanel = new NotificationActionPanel(gap, notification.getCollapseDirection()) {
      @Override
      public void uiDataSnapshot(@NotNull DataSink sink) {
        sink.set(Notification.KEY, notification);
      }
    };
    centerPanel.addActionPanel(actionPanel);

    List<AnAction> actions = notification.getActions();
    int actionsSize = actions.size();

    if (notification.isSuggestionType()) {
      if (actionsSize > 0) {
        AnAction action = actions.get(0);
        JButton button = new JButton(action.getTemplateText());
        button.setOpaque(false);
        setButtonColor(button, "Notification.Button.foreground", "JButton.textColor");
        setButtonColor(button, "Notification.Button.background", "JButton.backgroundColor");
        setButtonColor(button, "Notification.Button.borderColor", "JButton.borderColor");
        actionPanel.addAction(button);
        button.addActionListener(e -> {
          NotificationCollector.getInstance()
            .logNotificationActionInvoked(null, notification, action, NotificationCollector.NotificationPlace.BALLOON);
          Notification.fire(notification, action, DataManager.getInstance().getDataContext(button));
        });
        actionPanel.checkActionWidth = actionsSize > 1;

        if (actionsSize == 2) {
          actionPanel.addAction(createAction(notification, actions.get(1)));
        }
        else if (actionsSize > 2) {
          DefaultActionGroup group = new DefaultActionGroup();
          for (int i = 1; i < actionsSize; i++) {
            group.add(actions.get(i));
          }

          DropDownAction dropDownAction = new DropDownAction(IdeCoreBundle.message("notifications.action.more"),
                                                             (link, _1) -> showPopup(notification, link, group, actionPanel.popupAlarm));
          actionPanel.addAction(dropDownAction);
        }
      }
    }
    else {
      if (actionsSize > 1 && notification.getCollapseDirection() == Notification.CollapseActionsDirection.KEEP_RIGHTMOST) {
        addDropDownAction(notification, actionPanel);
      }

      for (AnAction action : actions) {
        actionPanel.addActionLink(createAction(notification, action));
      }

      if (actionsSize > 1 && notification.getCollapseDirection() == Notification.CollapseActionsDirection.KEEP_LEFTMOST) {
        addDropDownAction(notification, actionPanel);
      }
    }

    AnAction helpAction = notification.getContextHelpAction();
    if (helpAction != null) {
      Presentation presentation = helpAction.getTemplatePresentation();
      ContextHelpLabel helpLabel =
        ContextHelpLabel.create(StringUtil.defaultIfEmpty(presentation.getText(), ""), presentation.getDescription());
      helpLabel.setForeground(UIUtil.getLabelDisabledForeground());
      actionPanel.addAction(helpLabel);
    }

    Insets hover = JBUI.insets(8, 5, 8, 7);
    int count = actionPanel.getComponentCount();

    for (int i = 0; i < count; i++) {
      hoverAdapter.addComponent(actionPanel.getComponent(i), hover);
    }

    hoverAdapter.addSource(actionPanel);
  }

  private static void setButtonColor(@NotNull JButton button, @NotNull String colorKey, @NotNull String colorProperty) {
    if (UIManager.getColor(colorKey) != null) {
      button.putClientProperty(colorProperty, JBColor.namedColor(colorKey));
    }
  }

  private static @NotNull LinkLabel<AnAction> createAction(@NotNull Notification notification, @NotNull AnAction action) {
    Presentation presentation = action.getTemplatePresentation();
    @SuppressWarnings("DialogTitleCapitalization") String text =
      presentation.getText();  // action templates are unfit for the context :/
    return new LinkLabel<>(text, presentation.getIcon(), (link, _action) -> {
      NotificationCollector.getInstance()
        .logNotificationActionInvoked(null, notification, _action, NotificationCollector.NotificationPlace.BALLOON);
      Notification.fire(notification, _action, DataManager.getInstance().getDataContext(link));
    }, action) {
      @Override
      protected Color getTextColor() {
        return NotificationsUtil.getLinkButtonForeground();
      }
    };
  }

  private static void addDropDownAction(Notification notification, NotificationActionPanel actionPanel) {
    DropDownAction action = new DropDownAction(notification.getDropDownText(), (link, ignored) -> {
      NotificationActionPanel parent = (NotificationActionPanel)link.getParent();
      DefaultActionGroup group = new DefaultActionGroup();
      for (LinkLabel<AnAction> actionLink : parent.actionLinks) {
        if (!actionLink.isVisible()) {
          group.add(actionLink.getLinkData());
        }
      }
      showPopup(notification, link, group, actionPanel.popupAlarm);
    });
    action.setVisible(false);
    actionPanel.addGroupedActionsLink(action);
  }

  private static final class HoverAdapter extends MouseAdapter implements MouseMotionListener {
    private final List<Pair<Component, ?>> myComponents = new ArrayList<>();
    private List<Component> mySources = new ArrayList<>();
    private Component myLastComponent;

    public void addComponent(@NotNull Component component, @NotNull Function<? super Component, ? extends Rectangle> hover) {
      myComponents.add(Pair.create(component, hover));
    }

    public void addComponent(@NotNull Component component, @NotNull Insets hover) {
      myComponents.add(Pair.create(component, hover));
    }

    public void addSource(@NotNull Component component) {
      mySources.add(component);
    }

    public void initListeners() {
      if (!myComponents.isEmpty()) {
        for (Component source : mySources) {
          source.addMouseMotionListener(this);
          source.addMouseListener(this);
        }
        mySources = null;
      }
    }

    @Override
    public void mousePressed(MouseEvent e) {
      handleEvent(e, true, false);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      handleEvent(e, false, false);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      handleEvent(e, false, true);
    }

    @Override
    public void mouseExited(MouseEvent e) {
      if (myLastComponent != null) {
        mouseExited(e, myLastComponent);
        myLastComponent = null;
      }
    }

    private void handleEvent(MouseEvent e, boolean pressed, boolean moved) {
      if (e.getSource() instanceof JEditorPane pane) {
        int pos = pane.viewToModel2D(e.getPoint());
        if (pos >= 0) {
          HTMLDocument document = (HTMLDocument)pane.getDocument();
          AttributeSet attributes = document.getCharacterElement(pos).getAttributes();
          if (attributes.getAttribute(HTML.Tag.A) != null) {
            return;
          }
        }
      }

      for (Pair<Component, ?> p : myComponents) {
        Component component = p.first;
        Rectangle bounds;
        if (p.second instanceof Insets) {
          bounds = component.getBounds();
          JBInsets.addTo(bounds, (Insets)p.second);
        }
        else {
          @SuppressWarnings("unchecked") Function<Component, Rectangle> fun = (Function<Component, Rectangle>)p.second;
          bounds = fun.apply(component);
        }
        if (bounds.contains(SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), component.getParent()))) {
          if (myLastComponent != null && myLastComponent != component) {
            mouseExited(e, myLastComponent);
          }
          myLastComponent = component;

          MouseEvent event = createEvent(e, component);
          if (moved) {
            for (MouseMotionListener listener : component.getMouseMotionListeners()) {
              listener.mouseMoved(event);
            }
          }
          else {
            for (MouseListener listener : component.getMouseListeners()) {
              if (pressed) {
                listener.mousePressed(event);
              }
              else {
                listener.mouseReleased(event);
              }
            }
          }

          e.getComponent().setCursor(component.getCursor());
          return;
        }
        else if (component == myLastComponent) {
          myLastComponent = null;
          mouseExited(e, component);
        }
      }
    }

    private static void mouseExited(MouseEvent e, Component component) {
      e.getComponent().setCursor(null);

      MouseEvent event = createEvent(e, component);
      MouseListener[] listeners = component.getMouseListeners();
      for (MouseListener listener : listeners) {
        listener.mouseExited(event);
      }
    }

    @SuppressWarnings("deprecation")
    private static MouseEvent createEvent(MouseEvent e, Component c) {
      return new MouseEvent(c, e.getID(), e.getWhen(), e.getModifiers(), 5, 5, e.getClickCount(), e.isPopupTrigger(), e.getButton());
    }
  }

  private static void createMergeAction(BalloonLayoutData layoutData, JPanel panel) {
    @SuppressWarnings("removal") String shortTitle = NotificationParentGroup.getShortTitle(layoutData.groupId);
    String title = shortTitle != null ? IdeBundle.message("notification.manager.merge.n.more.from", layoutData.mergeData.count, shortTitle)
                                      : IdeBundle.message("notification.manager.merge.n.more", layoutData.mergeData.count);
    LinkListener<BalloonLayoutData> listener = (link, _layoutData) -> ActionCenter.showNotification(_layoutData.project);
    LinkLabel<BalloonLayoutData> action = new LinkLabel<>(title, null, listener, layoutData) {
      @Override
      protected boolean isInClickableArea(Point pt) {
        return true;
      }

      @Override
      protected Color getTextColor() {
        return NotificationsUtil.getMoreButtonForeground();
      }
    };

    action.setFont(FontUtil.minusOne(action.getFont()));
    action.setHorizontalAlignment(SwingConstants.CENTER);
    action.setPaintUnderline(false);

    AbstractLayoutManager layout = new AbstractLayoutManager() {
      @Override
      public Dimension preferredLayoutSize(Container parent) {
        return new Dimension(parent.getWidth(), JBUIScale.scale(20) + 2);
      }

      @Override
      public void layoutContainer(Container parent) {
        parent.getComponent(0).setBounds(2, 1, parent.getWidth() - 4, JBUIScale.scale(20));
      }
    };
    JPanel mergePanel = new NonOpaquePanel(layout) {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(NotificationsUtil.getMoreButtonBackground());
        ((Graphics2D)g).fill(new Rectangle2D.Double(1.5, 1, getWidth() - 2.5, getHeight() - 2));
        g.setColor(JBColor.namedColor("Notification.MoreButton.innerBorderColor", new JBColor(0xDBDBDB, 0x353738)));
        if (SystemInfo.isMac) {
          ((Graphics2D)g).draw(new Rectangle2D.Double(2, 0, getWidth() - 3.5, 0.5));
        }
        else if (SystemInfo.isWindows) {
          ((Graphics2D)g).draw(new Rectangle2D.Double(1.5, 0, getWidth() - 3, 0.5));
        }
        else {
          ((Graphics2D)g).draw(new Rectangle2D.Double(1.5, 0, getWidth() - 2.5, 0.5));
        }
      }
    };
    mergePanel.add(action);
    panel.add(BorderLayout.SOUTH, mergePanel);
  }

  public static int calculateContentHeight(int lines) {
    String word = IdeBundle.message("notification.manager.content.height.word");
    String lineBreak = IdeBundle.message("notification.manager.content.height.linebreak");
    String content = word + StringUtil.repeat(lineBreak + word, lines - 1);

    JEditorPane text = new JEditorPane();
    text.setEditorKit(HTMLEditorKitBuilder.simple());
    text.setText(NotificationsUtil.buildHtml(null, null, false, content, null, null, null, NotificationsUtil.getFontStyle()));
    text.setEditable(false);
    text.setOpaque(false);
    text.setBorder(null);

    return text.getPreferredSize().height;
  }

  static boolean isDummyEnvironment() {
    Application app = ApplicationManager.getApplication();
    return app.isUnitTestMode() || app.isCommandLine();
  }

  private static final class BalloonPopupSupport extends PopupMenuListenerAdapter implements Disposable {
    private final JPopupMenu myPopupMenu;
    private final JComponent myComponent;
    private final SingleEdtTaskScheduler myAlarm;
    private boolean myHandleDispose = true;

    private BalloonPopupSupport(@NotNull JPopupMenu popupMenu,
                                @NotNull JComponent component,
                                @NotNull SingleEdtTaskScheduler popupAlarm) {
      myPopupMenu = popupMenu;
      myComponent = component;
      myAlarm = popupAlarm;
    }

    private void setupListeners(@NotNull Balloon balloon) {
      myAlarm.cancel();
      myPopupMenu.addPopupMenuListener(this);
      Disposer.register(balloon, this);
    }

    @Override
    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
      myHandleDispose = false;
      Disposer.dispose(this);
      myComponent.putClientProperty("PopupHideInProgress", Boolean.TRUE);
      myAlarm.request(500, () -> myComponent.putClientProperty("PopupHideInProgress", null));
    }

    @Override
    public void dispose() {
      myPopupMenu.removePopupMenuListener(this);
      if (myHandleDispose) {
        myPopupMenu.setVisible(false);
      }
    }
  }

  public static class DropDownAction extends LinkLabel<Void> {
    public DropDownAction(@NlsContexts.LinkLabel String text, @Nullable LinkListener<Void> listener) {
      super(text, null, listener);

      setHorizontalTextPosition(SwingConstants.LEADING);
      setIconTextGap(JBUI.scale(1));

      setIcon(new Icon() {
        private final Icon icon = AllIcons.General.LinkDropTriangle;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
          icon.paintIcon(c, g, x, y + 1);
        }

        @Override
        public int getIconWidth() {
          return icon.getIconWidth();
        }

        @Override
        public int getIconHeight() {
          return icon.getIconHeight();
        }
      });
    }

    @Override
    protected Color getTextColor() {
      return NotificationsUtil.getLinkButtonForeground();
    }
  }

  private static void showPopup(@NotNull Notification notification,
                                @NotNull LinkLabel<?> link,
                                @NotNull DefaultActionGroup group,
                                @NotNull SingleEdtTaskScheduler popupAlarm) {
    if (link.getClientProperty("PopupHideInProgress") != null) {
      return;
    }
    JPopupMenu menu = showPopup(link, group);
    Balloon balloon = notification.getBalloon();
    if (menu != null && balloon != null) {
      new BalloonPopupSupport(menu, link, popupAlarm).setupListeners(balloon);
    }
  }

  public static @Nullable JPopupMenu showPopup(@NotNull LinkLabel<?> link, @NotNull DefaultActionGroup group) {
    if (link.isShowing()) {
      ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu("NotificationManager", group);
      JPopupMenu component = menu.getComponent();
      component.show(link, JBUIScale.scale(-10), link.getHeight() + JBUIScale.scale(2));
      return component;
    }
    return null;
  }


  private static @Nullable Point getCollapsedTextEndLocation(JEditorPane text, BalloonLayoutData layoutData) {
    try {
      int end = text.viewToModel2D(new Point(10, layoutData.twoLineHeight + 5));
      if (end == -1) {
        end = text.getDocument().getLength();
      }
      for (int i = end - 1; i >= 0; i--) {
        Rectangle2D r = text.modelToView2D(i);
        if (r != null && r.getY() < layoutData.twoLineHeight) {
          return r.getBounds().getLocation();
        }
      }
    }
    catch (BadLocationException ignored) {
    }
    return null;
  }

  private static int getFirstLineHeight(JEditorPane text) {
    try {
      int end = text.getDocument().getLength();
      for (int i = 0; i < end; i++) {
        Rectangle2D r = text.modelToView2D(i);
        if (r != null) {
          int height = (int)r.getHeight();
          if (height > 0) return height;
        }
      }
    }
    catch (BadLocationException ignored) {
    }
    return 0;
  }

  private static final class NotificationCenterPanel extends NonOpaquePanel {
    private final CenteredLayoutWithActions myLayout;
    private final BalloonLayoutData myLayoutData;

    private NotificationCenterPanel(JEditorPane text, BalloonLayoutData layoutData) {
      super(new CenteredLayoutWithActions(text, layoutData));
      myLayout = (CenteredLayoutWithActions)getLayout();
      myLayoutData = layoutData;
    }

    public void addTitle(JLabel title) {
      add(title, BorderLayout.NORTH);
      myLayout.myTitleComponent = title;
    }

    public Component getTitle() {
      return myLayout.getTitle();
    }

    public void addExpandAction(LinkLabel<Void> action) {
      add(action, BorderLayout.EAST);
      myLayout.myExpandAction = action;
    }

    public void addContent(JComponent component) {
      add(component, BorderLayout.CENTER);
      myLayout.myCenteredComponent = component;
    }

    public void addActionPanel(NotificationActionPanel panel) {
      add(panel, BorderLayout.SOUTH);
      myLayout.myActionPanel = panel;
    }

    @Override
    protected void paintChildren(Graphics g) {
      super.paintChildren(g);
      Component title = myLayout.getTitle();

      if (title != null && myLayoutData.showActions != null && myLayoutData.showActions.compute()) {
        int width = myLayoutData.configuration.allActionsOffset;
        int x = getWidth() - width - JBUIScale.scale(5);
        int y = myLayoutData.configuration.topSpaceHeight;
        int height = title instanceof JEditorPane ? getFirstLineHeight((JEditorPane)title) : title.getHeight();

        g.setColor(myLayoutData.fillColor);
        g.fillRect(x, y, width, height);

        width = myLayoutData.configuration.beforeGearSpace;
        x -= width;
        ((Graphics2D)g)
          .setPaint(new GradientPaint(x, y, ColorUtil.withAlpha(myLayoutData.fillColor, 0.2), x + width, y, myLayoutData.fillColor));
        g.fillRect(x, y, width, height);
      }
    }
  }

  private abstract static class NotificationActionPanel extends NonOpaquePanel implements UiDataProvider {
    private final List<LinkLabel<AnAction>> actionLinks = new ArrayList<>();
    private final Notification.CollapseActionsDirection collapseActionsDirection;
    private DropDownAction groupedActionsLink;
    boolean checkActionWidth;
    final SingleEdtTaskScheduler popupAlarm = SingleEdtTaskScheduler.createSingleEdtTaskScheduler();

    private NotificationActionPanel(int gap, Notification.CollapseActionsDirection direction) {
      super(new HorizontalLayout(gap, SwingConstants.CENTER));
      collapseActionsDirection = direction;
    }

    public void addGroupedActionsLink(DropDownAction action) {
      add(action);
      groupedActionsLink = action;
    }

    public void addActionLink(LinkLabel<AnAction> label) {
      add(HorizontalLayout.LEFT, label);
      actionLinks.add(label);
    }

    public void addAction(JComponent component) {
      add(HorizontalLayout.LEFT, component);
    }
  }

  private static final class CenteredLayoutWithActions extends BorderLayout {
    private final JEditorPane myText;
    private final BalloonLayoutData myLayoutData;
    private JLabel myTitleComponent;
    private Component myCenteredComponent;
    private NotificationActionPanel myActionPanel;
    private Component myExpandAction;

    CenteredLayoutWithActions(JEditorPane text, BalloonLayoutData layoutData) {
      myText = text;
      myLayoutData = layoutData;
    }

    public @Nullable Component getTitle() {
      if (myTitleComponent != null) {
        return myTitleComponent;
      }
      if (myCenteredComponent != null) {
        if (myCenteredComponent instanceof JScrollPane) {
          return ((JScrollPane)myCenteredComponent).getViewport().getView();
        }
        return myCenteredComponent;
      }
      return null;
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      return layoutSize(component -> component.getPreferredSize());
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      return layoutSize(component -> component.getMinimumSize());
    }

    private Dimension layoutSize(Function<? super Component, ? extends Dimension> size) {
      Dimension titleSize = myTitleComponent == null ? new Dimension() : size.apply(myTitleComponent);
      Dimension centeredSize = myCenteredComponent == null ? new Dimension() : size.apply(myCenteredComponent);
      Dimension actionSize = myActionPanel == null ? new Dimension() : size.apply(myActionPanel);
      Dimension expandSize = myExpandAction == null || myLayoutData.showMinSize ? new Dimension() : size.apply(myExpandAction);

      int height = myLayoutData.configuration.topSpaceHeight +
                   titleSize.height + centeredSize.height + Math.max(actionSize.height, expandSize.height) +
                   myLayoutData.configuration.bottomSpaceHeight;

      if (titleSize.height > 0 && centeredSize.height > 0) {
        height += myLayoutData.configuration.titleContentSpaceHeight;
      }
      if (centeredSize.height > 0 && actionSize.height > 0) {
        height += myLayoutData.configuration.contentActionsSpaceHeight;
      }
      if (titleSize.height > 0 && actionSize.height > 0) {
        height += myLayoutData.configuration.titleActionsSpaceHeight;
      }

      int titleWidth = titleSize.width + myLayoutData.configuration.closeOffset;
      int centerWidth = centeredSize.width + myLayoutData.configuration.closeOffset;
      int actionWidth = actionSize.width + expandSize.width;

      int width = Math.max(centerWidth, Math.max(titleWidth, actionWidth));
      if (!myLayoutData.showFullContent) {
        width = Math.min(width, BalloonLayoutConfiguration.MaxWidth());
      }
      width = Math.max(width, BalloonLayoutConfiguration.MinWidth());

      return new Dimension(width, height);
    }

    @Override
    public void layoutContainer(Container parent) {
      int top = myLayoutData.configuration.topSpaceHeight;
      int width = parent.getWidth();
      Dimension centeredSize = myCenteredComponent == null ? new Dimension() : myCenteredComponent.getPreferredSize();
      boolean isActions = myActionPanel != null || (myExpandAction != null && !myLayoutData.showMinSize);

      if (myTitleComponent != null) {
        int titleHeight = myTitleComponent.getPreferredSize().height;
        myTitleComponent.setBounds(0, top, width - myLayoutData.configuration.closeOffset, titleHeight);
        top += titleHeight;

        if (myCenteredComponent != null) {
          top += myLayoutData.configuration.titleContentSpaceHeight;
        }
        else if (isActions) {
          top += myLayoutData.configuration.titleActionsSpaceHeight;
        }
      }

      if (myCenteredComponent != null) {
        int centeredWidth = width;
        if (!myLayoutData.showFullContent && !myLayoutData.showMinSize && myLayoutData.fullHeight != myLayoutData.maxScrollHeight) {
          centeredWidth--;
        }
        myCenteredComponent.setBounds(0, top, centeredWidth, centeredSize.height);
        myCenteredComponent.revalidate();
      }

      if (myExpandAction != null) {
        Dimension size = myExpandAction.getPreferredSize();
        int x = width - size.width - Objects.requireNonNull(myLayoutData.configuration.rightActionsOffset).width;

        if (myLayoutData.showMinSize) {
          Point location = getCollapsedTextEndLocation(myText, myLayoutData);
          if (location == null) {
            location = new Point(10, myText.getHeight() - size.height);
          }
          int y = SwingUtilities.convertPoint(myText, location.x, location.y, parent).y;
          myExpandAction.setBounds(x, y, size.width, size.height);
        }
        else {
          int y = parent.getHeight() - size.height - myLayoutData.configuration.bottomSpaceHeight;
          myExpandAction.setBounds(x, y, size.width, size.height);
        }
      }

      if (myActionPanel != null) {
        int expandWidth = myExpandAction == null || myLayoutData.showMinSize ? 0 : myExpandAction.getPreferredSize().width;
        width -= myLayoutData.configuration.actionGap + expandWidth;

        if (myActionPanel.checkActionWidth && myActionPanel.getPreferredSize().width - width > 0 && width > 0) {
          Component component0 = myActionPanel.getComponent(0);
          Component component1 = myActionPanel.getComponent(1);
          Dimension size0 = component0.getPreferredSize();
          Dimension size1 = component1.getPreferredSize();
          int halfWidth = width / 2;

          if (size0.width > halfWidth && size1.width > halfWidth) {
            cutWidth(component0, size0, halfWidth);
            cutWidth(component1, size1, halfWidth);
          }
          else if (size0.width > halfWidth) {
            cutWidth(component0, size0, size0.width - myActionPanel.getPreferredSize().width + width);
          }
          else {
            cutWidth(component1, size1, size1.width - myActionPanel.getPreferredSize().width + width);
          }

          myActionPanel.checkActionWidth = false;
          myActionPanel.doLayout();
        }

        if (myActionPanel.actionLinks.size() > 1) {
          myActionPanel.groupedActionsLink.setVisible(false);
          for (LinkLabel<AnAction> link : myActionPanel.actionLinks) {
            link.setVisible(true);
          }
          myActionPanel.doLayout();

          boolean keepRightmost = myActionPanel.collapseActionsDirection == Notification.CollapseActionsDirection.KEEP_RIGHTMOST;
          int collapseStart = keepRightmost ? 0 : myActionPanel.actionLinks.size() - 1;
          int collapseDelta = keepRightmost ? 1 : -1;
          int collapseIndex = collapseStart;
          if (myActionPanel.getPreferredSize().width > width) {
            myActionPanel.groupedActionsLink.setVisible(true);
            myActionPanel.actionLinks.get(collapseIndex).setVisible(false);
            collapseIndex += collapseDelta;
            myActionPanel.doLayout();
            while (myActionPanel.getPreferredSize().width > width &&
                   collapseIndex >= 0 &&
                   collapseIndex < myActionPanel.actionLinks.size()) {
              myActionPanel.actionLinks.get(collapseIndex).setVisible(false);
              collapseIndex += collapseDelta;
              myActionPanel.doLayout();
            }
          }
        }

        Dimension size = myActionPanel.getPreferredSize();
        int y = parent.getHeight() - size.height - myLayoutData.configuration.bottomSpaceHeight;
        myActionPanel.setBounds(0, y, width, size.height);
      }
    }

    private static void cutWidth(@NotNull Component component, @NotNull Dimension size, int width) {
      size.width = width;
      component.setPreferredSize(size);
      if (component instanceof JButton button) {
        button.setToolTipText(button.getText());
      }
      else if (component instanceof JLabel label) {
        label.setToolTipText(label.getText());
      }
    }
  }

  private static final class TextCaret extends DefaultCaret implements UIResource {
    private final BalloonLayoutData myLayoutData;

    TextCaret(@NotNull BalloonLayoutData layoutData) {
      myLayoutData = layoutData;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (!myLayoutData.showMinSize) {
        super.mouseClicked(e);
      }
    }

    @Override
    public void mousePressed(MouseEvent e) {
      if (!myLayoutData.showMinSize) {
        super.mousePressed(e);
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      if (!myLayoutData.showMinSize) {
        super.mouseReleased(e);
      }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      if (!myLayoutData.showMinSize) {
        super.mouseEntered(e);
      }
    }

    @Override
    public void mouseExited(MouseEvent e) {
      if (!myLayoutData.showMinSize) {
        super.mouseExited(e);
      }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      if (!myLayoutData.showMinSize) {
        super.mouseDragged(e);
      }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (!myLayoutData.showMinSize) {
        super.mouseMoved(e);
      }
    }
  }
}
