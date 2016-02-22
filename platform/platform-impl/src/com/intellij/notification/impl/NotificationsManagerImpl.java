/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.notification.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.FrameStateManager;
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonPainter;
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI;
import com.intellij.notification.*;
import com.intellij.notification.impl.ui.NotificationsUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.ui.*;
import com.intellij.ui.components.GradientViewport;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.ColorUIResource;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author spleaner
 */
public class NotificationsManagerImpl extends NotificationsManager {
  public NotificationsManagerImpl() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(Notifications.TOPIC, new MyNotificationListener(null));
  }

  public static boolean newEnabled() {
    return Registry.is("ide.new.notification.enabled", false);
  }

  @Override
  public void expire(@NotNull final Notification notification) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        EventLog.expireNotification(notification);
      }
    });
  }

  @Override
  @NotNull
  public <T extends Notification> T[] getNotificationsOfType(@NotNull Class<T> klass, @Nullable final Project project) {
    final List<T> result = new ArrayList<T>();
    if (project == null || !project.isDefault() && !project.isDisposed()) {
      for (Notification notification : EventLog.getLogModel(project).getNotifications()) {
        if (klass.isInstance(notification)) {
          //noinspection unchecked
          result.add((T)notification);
        }
      }
    }
    return ArrayUtil.toObjectArray(result, klass);
  }

  private static void doNotify(@NotNull final Notification notification,
                               @Nullable NotificationDisplayType displayType,
                               @Nullable final Project project) {
    final NotificationsConfigurationImpl configuration = NotificationsConfigurationImpl.getInstanceImpl();
    if (!configuration.isRegistered(notification.getGroupId())) {
      configuration.register(notification.getGroupId(), displayType == null ? NotificationDisplayType.BALLOON : displayType);
    }

    final NotificationSettings settings = NotificationsConfigurationImpl.getSettings(notification.getGroupId());
    boolean shouldLog = settings.isShouldLog();
    boolean displayable = settings.getDisplayType() != NotificationDisplayType.NONE;

    boolean willBeShown = displayable && NotificationsConfigurationImpl.getInstanceImpl().SHOW_BALLOONS;
    if (!shouldLog && !willBeShown) {
      notification.expire();
    }

    if (NotificationsConfigurationImpl.getInstanceImpl().SHOW_BALLOONS) {
      final Runnable runnable = new DumbAwareRunnable() {
        @Override
        public void run() {
          showNotification(notification, project);
        }
      };
      if (project == null) {
        UIUtil.invokeLaterIfNeeded(runnable);
      }
      else if (!project.isDisposed()) {
        StartupManager.getInstance(project).runWhenProjectIsInitialized(runnable);
      }
    }
  }

  private static void showNotification(@NotNull final Notification notification, @Nullable final Project project) {
    Application application = ApplicationManager.getApplication();
    if (application instanceof ApplicationEx && !((ApplicationEx)application).isLoaded()) {
      application.invokeLater(new Runnable() {
        @Override
        public void run() {
          showNotification(notification, project);
        }
      }, ModalityState.current());
      return;
    }


    String groupId = notification.getGroupId();
    final NotificationSettings settings = NotificationsConfigurationImpl.getSettings(groupId);

    NotificationDisplayType type = settings.getDisplayType();
    String toolWindowId = NotificationsConfigurationImpl.getInstanceImpl().getToolWindowId(groupId);
    if (type == NotificationDisplayType.TOOL_WINDOW &&
        (toolWindowId == null || project == null || !ToolWindowManager.getInstance(project).canShowNotification(toolWindowId))) {
      type = NotificationDisplayType.BALLOON;
    }

    switch (type) {
      case NONE:
        return;
      //case EXTERNAL:
      //  notifyByExternal(notification);
      //  break;
      case STICKY_BALLOON:
      case BALLOON:
      default:
        Balloon balloon = notifyByBalloon(notification, type, project);
        if (!settings.isShouldLog() || type == NotificationDisplayType.STICKY_BALLOON) {
          if (balloon == null) {
            notification.expire();
          }
          else {
            balloon.addListener(new JBPopupAdapter() {
              @Override
              public void onClosed(LightweightWindowEvent event) {
                if (!event.isOk()) {
                  notification.expire();
                }
              }
            });
          }
        }
        break;
      case TOOL_WINDOW:
        MessageType messageType = notification.getType() == NotificationType.ERROR
                                  ? MessageType.ERROR
                                  : notification.getType() == NotificationType.WARNING ? MessageType.WARNING : MessageType.INFO;
        final NotificationListener notificationListener = notification.getListener();
        HyperlinkListener listener = notificationListener == null ? null : new HyperlinkListener() {
          @Override
          public void hyperlinkUpdate(HyperlinkEvent e) {
            notificationListener.hyperlinkUpdate(notification, e);
          }
        };
        assert toolWindowId != null;
        String msg = notification.getTitle();
        if (StringUtil.isNotEmpty(notification.getContent())) {
          if (StringUtil.isNotEmpty(msg)) {
            msg += "<br>";
          }
          msg += notification.getContent();
        }

        //noinspection SSBasedInspection
        ToolWindowManager.getInstance(project).notifyByBalloon(toolWindowId, messageType, msg, notification.getIcon(), listener);
    }
  }

  @Nullable
  private static Balloon notifyByBalloon(@NotNull final Notification notification,
                                         @NotNull final NotificationDisplayType displayType,
                                         @Nullable final Project project) {
    if (isDummyEnvironment()) return null;

    Window window = findWindowForBalloon(project);
    if (window instanceof IdeFrame) {
      final ProjectManager projectManager = ProjectManager.getInstance();
      final boolean noProjects = projectManager.getOpenProjects().length == 0;
      final boolean sticky = NotificationDisplayType.STICKY_BALLOON == displayType || noProjects;
      Ref<Object> layoutDataRef = newEnabled() && notification.getIcon() != null ? new Ref<Object>() : null;
      final Balloon balloon = createBalloon((IdeFrame)window, notification, false, false, layoutDataRef);
      Disposer.register(project != null ? project : ApplicationManager.getApplication(), balloon);

      if (notification.isExpired()) {
        return null;
      }

      BalloonLayout layout = ((IdeFrame)window).getBalloonLayout();

      if (layout == null) return null;
      layout.add(balloon, layoutDataRef == null ? null : layoutDataRef.get());
      if (layoutDataRef != null && layoutDataRef.get() instanceof BalloonLayoutData) {
        ((BalloonLayoutData)layoutDataRef.get()).project = project;
      }
      ((BalloonImpl)balloon).startFadeoutTimer(0);
      if (NotificationDisplayType.BALLOON == displayType) {
        FrameStateManager.getInstance().getApplicationActive().doWhenDone(new Runnable() {
          @Override
          public void run() {
            if (balloon.isDisposed()) {
              return;
            }

            if (!sticky) {
              ((BalloonImpl)balloon).startFadeoutTimer(0);
              ((BalloonImpl)balloon).setHideOnClickOutside(true);
            }
            else //noinspection ConstantConditions
              if (noProjects) {
                projectManager.addProjectManagerListener(new ProjectManagerAdapter() {
                  @Override
                  public void projectOpened(Project project) {
                    projectManager.removeProjectManagerListener(this);
                    if (!balloon.isDisposed()) {
                      ((BalloonImpl)balloon).startFadeoutTimer(300);
                    }
                  }
                });
              }
          }
        });
      }
      return balloon;
    }
    return null;
  }

  @Nullable
  public static Window findWindowForBalloon(@Nullable Project project) {
    Window frame = WindowManager.getInstance().getFrame(project);
    if (frame == null && project == null) {
      frame = (Window)WelcomeFrame.getInstance();
    }
    if (frame == null && project == null) {
      frame = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      while (frame instanceof DialogWrapperDialog && ((DialogWrapperDialog)frame).getDialogWrapper().isModalProgress()) {
        frame = frame.getOwner();
      }
    }
    return frame;
  }

  @NotNull
  public static Balloon createBalloon(@NotNull final IdeFrame window,
                                      @NotNull final Notification notification,
                                      final boolean showCallout,
                                      final boolean hideOnClickOutside,
                                      @Nullable Ref<Object> layoutDataRef) {
    return createBalloon(window.getComponent(), notification, showCallout, hideOnClickOutside, layoutDataRef);
  }

  @NotNull
  public static Balloon createBalloon(@Nullable final JComponent windowComponent,
                                      @NotNull final Notification notification,
                                      final boolean showCallout,
                                      final boolean hideOnClickOutside,
                                      @Nullable Ref<Object> layoutDataRef) {
    if (layoutDataRef != null) {
      return createNewBalloon(windowComponent, notification, showCallout, hideOnClickOutside, layoutDataRef);
    }

    final JEditorPane text = new JEditorPane();
    text.setEditorKit(UIUtil.getHTMLEditorKit());

    final HyperlinkListener listener = NotificationsUtil.wrapListener(notification);
    if (listener != null) {
      text.addHyperlinkListener(listener);
    }

    final JLabel label = new JLabel(NotificationsUtil.buildHtml(notification, null));
    text.setText(NotificationsUtil.buildHtml(notification, "width:" + Math.min(JBUI.scale(350), label.getPreferredSize().width) + "px;"));
    text.setEditable(false);
    text.setOpaque(false);

    if (UIUtil.isUnderNimbusLookAndFeel()) {
      text.setBackground(UIUtil.TRANSPARENT_COLOR);
    }

    text.setBorder(null);

    final JPanel content = new NonOpaquePanel(new BorderLayout((int)(label.getIconTextGap() * 1.5), (int)(label.getIconTextGap() * 1.5)));

    if (text.getCaret() != null) {
      text.setCaretPosition(0);
    }
    JScrollPane pane = ScrollPaneFactory.createScrollPane(text, true); // do not add 1px border for viewport on UI update
    pane.setOpaque(false);
    pane.getViewport().setOpaque(false);
    content.add(pane, BorderLayout.CENTER);

    final NonOpaquePanel north = new NonOpaquePanel(new BorderLayout());
    north.add(new JLabel(NotificationsUtil.getIcon(notification)), BorderLayout.NORTH);
    content.add(north, BorderLayout.WEST);

    content.setBorder(new EmptyBorder(2, 4, 2, 4));

    JPanel buttons = createButtons(notification, content, listener);

    Dimension preferredSize = text.getPreferredSize();
    text.setSize(preferredSize);

    Dimension paneSize = new Dimension(text.getPreferredSize());

    int maxHeight = JBUI.scale(400);
    int maxWidth = JBUI.scale(600);

    if (windowComponent != null) {
      maxHeight = Math.min(maxHeight, windowComponent.getHeight() - 20);
      maxWidth = Math.min(maxWidth, windowComponent.getWidth() - 20);
    }

    if (paneSize.height > maxHeight) {
      pane.setPreferredSize(new Dimension(Math.min(maxWidth, paneSize.width + UIUtil.getScrollBarWidth()), maxHeight));
    }
    else if (paneSize.width > maxWidth) {
      pane.setPreferredSize(new Dimension(maxWidth, paneSize.height + UIUtil.getScrollBarWidth()));
    }

    final BalloonBuilder builder = JBPopupFactory.getInstance().createBalloonBuilder(content);
    builder.setFillColor(new JBColor(Gray._234, Gray._92))
      .setCloseButtonEnabled(buttons == null)
      .setShowCallout(showCallout)
      .setShadow(false)
      .setHideOnClickOutside(hideOnClickOutside)
      .setHideOnAction(hideOnClickOutside)
      .setHideOnKeyOutside(hideOnClickOutside)
      .setHideOnFrameResize(false)
      .setBorderColor(new JBColor(Gray._180, Gray._110));

    final Balloon balloon = builder.createBalloon();
    balloon.setAnimationEnabled(false);
    notification.setBalloon(balloon);
    return balloon;
  }

  @Nullable
  private static JPanel createButtons(@NotNull Notification notification,
                                      @NotNull final JPanel content,
                                      @Nullable HyperlinkListener listener) {
    if (notification instanceof NotificationActionProvider) {
      JPanel buttons = new JPanel(new HorizontalLayout(5));
      buttons.setOpaque(false);
      content.add(BorderLayout.SOUTH, buttons);

      final Ref<JButton> defaultButton = new Ref<JButton>();

      NotificationActionProvider provider = (NotificationActionProvider)notification;
      for (NotificationActionProvider.Action action : provider.getActions(listener)) {
        JButton button = new JButton(action) {
          @Override
          public void setUI(ButtonUI ui) {
            boolean isDarcula = ui instanceof DarculaButtonUI && UIUtil.isUnderDarcula();
            if (isDarcula) {
              ui = new DarculaButtonUI() {
                @Override
                protected Color getButtonColor1() {
                  return new ColorUIResource(0x464b4c);
                }

                @Override
                protected Color getButtonColor2() {
                  return new ColorUIResource(0x383c3d);
                }
              };
            }
            super.setUI(ui);
            if (isDarcula) {
              setBorder(new DarculaButtonPainter() {
                @Override
                protected Color getBorderColor() {
                  return new ColorUIResource(0x616263);
                }
              });
            }
          }
        };

        button.setOpaque(false);
        if (action.isDefaultAction()) {
          defaultButton.setIfNull(button);
        }

        buttons.add(HorizontalLayout.RIGHT, button);
      }

      if (!defaultButton.isNull()) {
        UIUtil.addParentChangeListener(content, new PropertyChangeListener() {
          @Override
          public void propertyChange(PropertyChangeEvent event) {
            if (event.getOldValue() == null && event.getNewValue() != null) {
              UIUtil.removeParentChangeListener(content, this);
              JRootPane rootPane = UIUtil.getRootPane(content);
              if (rootPane != null) {
                rootPane.setDefaultButton(defaultButton.get());
              }
            }
          }
        });
      }

      return buttons;
    }
    return null;
  }

  @NotNull
  private static Balloon createNewBalloon(@Nullable JComponent windowComponent,
                                          @NotNull Notification notification,
                                          boolean showCallout,
                                          boolean hideOnClickOutside,
                                          @NotNull Ref<Object> layoutDataRef) {
    final BalloonLayoutData layoutData = new BalloonLayoutData();
    layoutDataRef.set(layoutData);

    Color foregroundR = Gray._0;
    Color foregroundD = Gray._191;
    final Color foreground = new JBColor(foregroundR, foregroundD);

    final JBColor fillColor = new JBColor(Gray._242, new Color(78, 80, 82));

    final JEditorPane text = new JEditorPane() {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (layoutData.showMinSize) {
          Point location = getCollapsedTextEndLocation(this, layoutData);
          if (location != null) {
            g.setColor(getForeground());
            g.drawString("...", location.x, location.y + g.getFontMetrics().getAscent());
          }
        }
      }
    };
    text.setEditorKit(UIUtil.getHTMLEditorKit());
    text.setForeground(foreground);

    final HyperlinkListener listener = NotificationsUtil.wrapListener(notification);
    if (listener != null) {
      text.addHyperlinkListener(listener);
    }

    String fontStyle = NotificationsUtil.getFontStyle();
    int prefSize = new JLabel(NotificationsUtil.buildHtml(notification, null, true, null, fontStyle)).getPreferredSize().width;
    String style = prefSize > BalloonLayoutConfiguration.MaxWidth ? BalloonLayoutConfiguration.MaxWidthStyle : null;

    String textR = NotificationsUtil.buildHtml(notification, style, true, foregroundR, fontStyle);
    String textD = NotificationsUtil.buildHtml(notification, style, true, foregroundD, fontStyle);
    LafHandler lafHandler = new LafHandler(text, textR, textD);
    layoutData.lafHandler = lafHandler;

    text.setEditable(false);
    text.setOpaque(false);

    if (UIUtil.isUnderNimbusLookAndFeel()) {
      text.setBackground(UIUtil.TRANSPARENT_COLOR);
    }

    text.setBorder(null);

    final JPanel content = new NonOpaquePanel(new BorderLayout());

    if (text.getCaret() != null) {
      text.setCaretPosition(0);
    }

    final JScrollPane pane = ScrollPaneFactory.createScrollPane(text, true);
    pane.setOpaque(false);

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

    List<AnAction> actions = notification.getActions();
    LinkLabel<Void> expandAction = null;

    int lines = 3;
    if (notification.isTitle()) {
      lines--;
    }
    if (!actions.isEmpty()) {
      lines--;
    }

    layoutData.fullHeight = text.getPreferredSize().height;
    layoutData.twoLineHeight = calculateContentHeight(lines);
    layoutData.maxScrollHeight = Math.min(layoutData.fullHeight, calculateContentHeight(10));
    layoutData.configuration = BalloonLayoutConfiguration.create(notification, layoutData);

    boolean showFullContent = notification instanceof NotificationActionProvider;

    if (!showFullContent && layoutData.maxScrollHeight != layoutData.fullHeight) {
      pane.setViewport(new GradientViewport(text, JBUI.insets(10, 0), true) {
        @Nullable
        @Override
        protected Color getViewColor() {
          return fillColor;
        }

        @Override
        protected void paintGradient(Graphics g) {
          if (!layoutData.showMinSize) {
            super.paintGradient(g);
          }
        }
      });
    }

    pane.getViewport().setOpaque(false);
    if (!Registry.is("ide.scroll.new.layout")) {
      pane.getVerticalScrollBar().setUI(ButtonlessScrollBarUI.createTransparent());
    }
    pane.setBackground(fillColor);
    pane.getViewport().setBackground(fillColor);
    pane.getVerticalScrollBar().setBackground(fillColor);

    if (!showFullContent && layoutData.twoLineHeight < layoutData.fullHeight) {
      text.setPreferredSize(null);
      Dimension size = text.getPreferredSize();
      size.height = layoutData.twoLineHeight;
      text.setPreferredSize(size);
      text.setSize(size);
      layoutData.showMinSize = true;

      pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
      pane.setPreferredSize(size);

      expandAction = new LinkLabel<Void>(null, AllIcons.Ide.Notification.Expand, new LinkListener<Void>() {
        @Override
        public void linkSelected(LinkLabel link, Void ignored) {
          layoutData.showMinSize = !layoutData.showMinSize;

          text.setPreferredSize(null);
          Dimension size = text.getPreferredSize();

          if (layoutData.showMinSize) {
            size.height = layoutData.twoLineHeight;
            pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
            link.setIcon(AllIcons.Ide.Notification.Expand);
            link.setHoveringIcon(AllIcons.Ide.Notification.ExpandHover);
          }
          else {
            size.height = layoutData.fullHeight;
            pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            link.setIcon(AllIcons.Ide.Notification.Collapse);
            link.setHoveringIcon(AllIcons.Ide.Notification.CollapseHover);
          }

          text.setPreferredSize(size);
          text.setSize(size);

          if (!layoutData.showMinSize) {
            size = new Dimension(size.width, layoutData.maxScrollHeight);
          }
          pane.setPreferredSize(size);

          content.doLayout();
          layoutData.doLayout.run();
        }
      });
      expandAction.setHoveringIcon(AllIcons.Ide.Notification.ExpandHover);
    }

    final CenteredLayoutWithActions layout = new CenteredLayoutWithActions(text, layoutData);
    JPanel centerPanel = new NonOpaquePanel(layout) {
      @Override
      protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        Component title = layout.getTitle();

        if (title != null && layoutData.showActions != null && layoutData.showActions.compute()) {
          int width = layoutData.configuration.allActionsOffset;
          int x = getWidth() - width - JBUI.scale(5);
          int y = layoutData.configuration.topSpaceHeight;

          int height = title instanceof JEditorPane ? getFirstLineHeight((JEditorPane)title) : title.getHeight();

          g.setColor(fillColor);
          g.fillRect(x, y, width, height);

          width = layoutData.configuration.beforeGearSpace;
          x -= width;
          ((Graphics2D)g).setPaint(new GradientPaint(x, y, ColorUtil.withAlpha(fillColor, 0.2), x + width, y, fillColor));
          g.fillRect(x, y, width, height);
        }
      }
    };
    content.add(centerPanel, BorderLayout.CENTER);

    if (notification.isTitle()) {
      String titleStyle = StringUtil.defaultIfEmpty(fontStyle, "") + "white-space:nowrap;";
      String titleR = NotificationsUtil.buildHtml(notification, titleStyle, false, foregroundR, null);
      String titleD = NotificationsUtil.buildHtml(notification, titleStyle, false, foregroundD, null);
      JLabel title = new JLabel();
      lafHandler.setTitle(title, titleR, titleD);
      title.setOpaque(false);
      if (UIUtil.isUnderNimbusLookAndFeel()) {
        title.setBackground(UIUtil.TRANSPARENT_COLOR);
      }
      title.setForeground(foreground);
      centerPanel.add(title, BorderLayout.NORTH);
    }

    if (expandAction != null) {
      centerPanel.add(expandAction, BorderLayout.EAST);
    }

    if (notification.isContent()) {
      centerPanel.add(pane, BorderLayout.CENTER);
    }

    final Icon icon = NotificationsUtil.getIcon(notification);
    JComponent iconComponent = new JComponent() {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        icon.paintIcon(this, g, layoutData.configuration.iconOffset.width, layoutData.configuration.iconOffset.height);
      }
    };
    iconComponent.setOpaque(false);
    iconComponent.setPreferredSize(
      new Dimension(layoutData.configuration.iconPanelWidth, 2 * layoutData.configuration.iconOffset.height + icon.getIconHeight()));

    content.add(iconComponent, BorderLayout.WEST);

    JPanel buttons = createButtons(notification, content, listener);
    if (buttons != null) {
      buttons.setBorder(new EmptyBorder(0, 0, JBUI.scale(5), JBUI.scale(7)));
    }

    if (buttons == null && !actions.isEmpty()) {
      createActionPanel(notification, centerPanel, layoutData.configuration.actionGap);
    }

    text.setSize(text.getPreferredSize());

    Dimension paneSize = new Dimension(text.getPreferredSize());
    int maxWidth = JBUI.scale(600);
    if (windowComponent != null) {
      maxWidth = Math.min(maxWidth, windowComponent.getWidth() - 20);
    }
    if (paneSize.width > maxWidth) {
      pane.setPreferredSize(new Dimension(maxWidth, paneSize.height + UIUtil.getScrollBarWidth()));
    }

    JBColor borderColor = new JBColor(Gray._178.withAlpha(205), new Color(86, 90, 92, 205));

    final BalloonBuilder builder = JBPopupFactory.getInstance().createBalloonBuilder(content);
    builder.setFillColor(fillColor)
      .setCloseButtonEnabled(buttons == null)
      .setShowCallout(showCallout)
      .setShadow(false)
      .setHideOnClickOutside(hideOnClickOutside)
      .setHideOnAction(hideOnClickOutside)
      .setHideOnKeyOutside(hideOnClickOutside)
      .setHideOnFrameResize(false)
      .setBorderColor(borderColor)
      .setBorderInsets(new Insets(0, 0, 0, 0));

    final BalloonImpl balloon = (BalloonImpl)builder.createBalloon();
    balloon.setAnimationEnabled(false);
    notification.setBalloon(balloon);

    balloon.setShadowBorderProvider(new NotificationBalloonShadowBorderProvider(fillColor, borderColor));

    if (buttons == null) {
      balloon.setActionProvider(
        new NotificationBalloonActionProvider(balloon, layout.getTitle(), layoutData, notification.getGroupId()));
    }

    return balloon;
  }

  private static void createActionPanel(@NotNull Notification notification, @NotNull JPanel centerPanel, int gap) {
    JPanel actionPanel = new NonOpaquePanel(new HorizontalLayout(gap, SwingConstants.CENTER));
    centerPanel.add(BorderLayout.SOUTH, actionPanel);

    List<AnAction> actions = notification.getActions();

    if (actions.size() > 2) {
      DropDownAction action = new DropDownAction(notification.getDropDownText(), new LinkListener<Void>() {
        @Override
        public void linkSelected(LinkLabel link, Void ignored) {
          Container parent = link.getParent();
          int size = parent.getComponentCount();
          DefaultActionGroup group = new DefaultActionGroup();
          for (int i = 1; i < size; i++) {
            Component component = parent.getComponent(i);
            if (!component.isVisible()) {
              group.add(((LinkLabel<AnAction>)component).getLinkData());
            }
          }
          ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group);
          menu.getComponent().show(link, JBUI.scale(-10), link.getHeight() + JBUI.scale(2));
        }
      });
      action.setVisible(false);
      actionPanel.add(action);
    }

    for (AnAction action : actions) {
      Presentation presentation = action.getTemplatePresentation();
      actionPanel.add(
        HorizontalLayout.LEFT,
        new LinkLabel<AnAction>(presentation.getText(), presentation.getIcon(), new LinkListener<AnAction>() {
          @Override
          public void linkSelected(LinkLabel aSource, AnAction action) {
            Notification.fire(action);
          }
        }, action));
    }
  }

  public static int calculateContentHeight(int lines) {
    JEditorPane text = new JEditorPane();
    text.setEditorKit(UIUtil.getHTMLEditorKit());
    text
      .setText(NotificationsUtil.buildHtml(null, null, "Content" + StringUtil.repeat("<br>\nContent", lines - 1), null, null, null,
                                           NotificationsUtil.getFontStyle()));
    text.setEditable(false);
    text.setOpaque(false);
    text.setBorder(null);

    return text.getPreferredSize().height;
  }

  private static boolean isDummyEnvironment() {
    final Application application = ApplicationManager.getApplication();
    return application.isUnitTestMode() || application.isCommandLine();
  }

  public static class ProjectNotificationsComponent {
    public ProjectNotificationsComponent(@NotNull final Project project) {
      if (isDummyEnvironment()) {
        return;
      }
      project.getMessageBus().connect().subscribe(Notifications.TOPIC, new MyNotificationListener(project));
    }
  }

  private static class DropDownAction extends LinkLabel<Void> {
    Icon myIcon = AllIcons.Ide.Notification.DropTriangle;

    public DropDownAction(String text, @Nullable LinkListener<Void> listener) {
      super(text, null, listener);

      setHorizontalTextPosition(SwingConstants.LEADING);
      setIconTextGap(0);

      setIcon(new Icon() {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
          int lineY = getUI().getBaseline(DropDownAction.this, getWidth(), getHeight()) - getIconHeight();
          IconUtil.colorize(myIcon, getTextColor()).paintIcon(c, g, x - 1, lineY);
        }

        @Override
        public int getIconWidth() {
          return myIcon.getIconWidth();
        }

        @Override
        public int getIconHeight() {
          return myIcon.getIconHeight();
        }
      });
    }

    @NotNull
    @Override
    protected Rectangle getTextBounds() {
      Rectangle bounds = super.getTextBounds();
      bounds.x -= getIcon().getIconWidth();
      bounds.width += 8;
      return bounds;
    }
  }

  private static class MyNotificationListener extends NotificationsAdapter {
    private final Project myProject;

    private MyNotificationListener(@Nullable Project project) {
      myProject = project;
    }

    @Override
    public void notify(@NotNull Notification notification) {
      doNotify(notification, null, myProject);
    }
  }

  @Nullable
  private static Point getCollapsedTextEndLocation(@NotNull JEditorPane text, @NotNull BalloonLayoutData layoutData) {
    try {
      int end = text.viewToModel(new Point(10, layoutData.twoLineHeight + 5));
      if (end == -1) {
        end = text.getDocument().getLength();
      }
      for (int i = end - 1; i >= 0; i--) {
        Rectangle r = text.modelToView(i);
        if (r != null && r.y < layoutData.twoLineHeight) {
          return r.getLocation();
        }
      }
    }
    catch (BadLocationException ignored) {
    }

    return null;
  }

  private static int getFirstLineHeight(@NotNull JEditorPane text) {
    try {
      int end = text.getDocument().getLength();
      for (int i = 0; i < end; i++) {
        Rectangle r = text.modelToView(i);
        if (r != null && r.height > 0) {
          return r.height;
        }
      }
    }
    catch (BadLocationException ignored) {
    }
    return 0;
  }

  private static class CenteredLayoutWithActions extends BorderLayout {
    private final JEditorPane myText;
    private final BalloonLayoutData myLayoutData;
    private Component myTitleComponent;
    private JScrollPane myCenteredComponent;
    private JPanel myActionPanel;
    private Component myExpandAction;

    public CenteredLayoutWithActions(JEditorPane text, BalloonLayoutData layoutData) {
      myText = text;
      myLayoutData = layoutData;
    }

    @Nullable
    public Component getTitle() {
      if (myTitleComponent != null) {
        return myTitleComponent;
      }
      if (myCenteredComponent != null) {
        return myCenteredComponent.getViewport().getView();
      }
      return null;
    }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
      if (BorderLayout.NORTH.equals(constraints)) {
        myTitleComponent = comp;
      }
      else if (BorderLayout.CENTER.equals(constraints)) {
        myCenteredComponent = (JScrollPane)comp;
      }
      else if (BorderLayout.SOUTH.equals(constraints)) {
        myActionPanel = (JPanel)comp;
      }
      else if (BorderLayout.EAST.equals(constraints)) {
        myExpandAction = comp;
      }
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
      addLayoutComponent(comp, name);
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      return layoutSize(new Function<Component, Dimension>() {
        @Override
        public Dimension fun(Component component) {
          return component.getPreferredSize();
        }
      });
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      return layoutSize(new Function<Component, Dimension>() {
        @Override
        public Dimension fun(Component component) {
          return component.getMinimumSize();
        }
      });
    }

    private Dimension layoutSize(@NotNull Function<Component, Dimension> size) {
      Dimension titleSize = myTitleComponent == null ? new Dimension() : size.fun(myTitleComponent);
      Dimension centeredSize = myCenteredComponent == null ? new Dimension() : size.fun(myCenteredComponent);
      Dimension actionSize = myActionPanel == null ? new Dimension() : size.fun(myActionPanel);
      Dimension expandSize = myExpandAction == null || myLayoutData.showMinSize ? new Dimension() : size.fun(myExpandAction);

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
      width = Math.min(width, BalloonLayoutConfiguration.MaxWidth);
      width = Math.max(width, BalloonLayoutConfiguration.MinWidth);

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
        myCenteredComponent.setBounds(0, top, width, centeredSize.height);
        myCenteredComponent.revalidate();
      }

      if (myExpandAction != null) {
        Dimension size = myExpandAction.getPreferredSize();
        int x = width - size.width - myLayoutData.configuration.rightActionsOffset.width;

        if (myLayoutData.showMinSize) {
          Point location = getCollapsedTextEndLocation(myText, myLayoutData);
          if (location != null) {
            int y = SwingUtilities.convertPoint(myText, location.x, location.y, parent).y;
            myExpandAction.setBounds(x, y, size.width, size.height);
          }
        }
        else {
          int y = parent.getHeight() - size.height - myLayoutData.configuration.bottomSpaceHeight;
          myExpandAction.setBounds(x, y, size.width, size.height);
        }
      }

      if (myActionPanel != null) {
        int expandWidth = myExpandAction == null || myLayoutData.showMinSize ? 0 : myExpandAction.getPreferredSize().width;
        width -= myLayoutData.configuration.actionGap + expandWidth;

        int components = myActionPanel.getComponentCount();
        if (components > 2) {
          myActionPanel.getComponent(0).setVisible(false);
          for (int i = 1; i < components; i++) {
            Component component = myActionPanel.getComponent(i);
            if (component.isVisible()) {
              break;
            }
            component.setVisible(true);
          }
          myActionPanel.doLayout();
          if (myActionPanel.getPreferredSize().width > width) {
            myActionPanel.getComponent(0).setVisible(true);
            myActionPanel.getComponent(1).setVisible(false);
            myActionPanel.getComponent(2).setVisible(false);
            myActionPanel.doLayout();
            for (int i = 3; i < components - 1; i++) {
              if (myActionPanel.getPreferredSize().width > width) {
                myActionPanel.getComponent(i).setVisible(false);
                myActionPanel.doLayout();
              }
              else {
                break;
              }
            }
          }
        }

        Dimension size = myActionPanel.getPreferredSize();
        int y = parent.getHeight() - size.height - myLayoutData.configuration.bottomSpaceHeight;
        myActionPanel.setBounds(0, y, width, size.height);
      }
    }
  }

  private static class LafHandler implements Runnable {
    private final JEditorPane myContent;
    private final String myContentTextR;
    private final String myContentTextD;

    private JLabel myTitle;
    private String myTitleTextR;
    private String myTitleTextD;

    public LafHandler(@NotNull JEditorPane content, @NotNull String textR, @NotNull String textD) {
      myContent = content;
      myContentTextR = textR;
      myContentTextD = textD;
      updateContent();
    }

    public void setTitle(@NotNull JLabel title, @NotNull String textR, @NotNull String textD) {
      myTitle = title;
      myTitleTextR = textR;
      myTitleTextD = textD;
      updateTitle();
    }

    private void updateTitle() {
      myTitle.setText(UIUtil.isUnderDarcula() ? myTitleTextD : myTitleTextR);
    }

    private void updateContent() {
      myContent.setText(UIUtil.isUnderDarcula() ? myContentTextD : myContentTextR);
    }

    @Override
    public void run() {
      if (myTitle != null) {
        updateTitle();
      }
      updateContent();
    }
  }
}