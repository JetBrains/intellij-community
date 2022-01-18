// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.diagnostic.IdeMessagePanel;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.notification.impl.widget.IdeNotificationArea;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.BalloonHandler;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.NlsContexts.PopupContent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetWrapper;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsActionGroup;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.popup.NotificationPopup;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

public class IdeStatusBarImpl extends JComponent implements Accessible, StatusBarEx, IdeEventQueue.EventDispatcher, DataProvider {
  private static final Logger LOG = Logger.getInstance(IdeStatusBarImpl.class);
  public static final DataKey<String> HOVERED_WIDGET_ID = DataKey.create("HOVERED_WIDGET_ID");
  public static final Key<WidgetEffect> WIDGET_EFFECT_KEY = Key.create("TextPanel.widgetEffect");
  public static final String NAVBAR_WIDGET_KEY = "NavBar";

  private static final String WIDGET_ID = "STATUS_BAR_WIDGET_ID";
  private static final int MIN_ICON_HEIGHT = JBUI.scale(18 + 1 + 1);

  private final InfoAndProgressPanel myInfoAndProgressPanel;
  @NotNull
  private final IdeFrame myFrame;

  private enum Position {LEFT, RIGHT, CENTER}

  public enum WidgetEffect { HOVER, PRESSED }

  private static final String uiClassID = "IdeStatusBarUI";

  private final Map<String, WidgetBean> myWidgetMap = new LinkedHashMap<>();

  private JPanel myLeftPanel;
  private JPanel myRightPanel;
  private JPanel myCenterPanel;
  private Component myEffectComponent;

  private @NlsContexts.StatusBarText String myInfo;

  private final List<String> myCustomComponentIds = new ArrayList<>();

  private final Set<IdeStatusBarImpl> myChildren = new HashSet<>();

  private final EventDispatcher<StatusBarListener> myListeners = EventDispatcher.create(StatusBarListener.class);

  private static final class WidgetBean {
    JComponent component;
    Position position;
    StatusBarWidget widget;
    String anchor;

    static WidgetBean create(@NotNull final StatusBarWidget widget,
                             @NotNull final Position position,
                             @NotNull final JComponent component,
                             @NotNull String anchor) {
      final WidgetBean bean = new WidgetBean();
      bean.widget = widget;
      bean.position = position;
      bean.component = component;
      bean.anchor = anchor;
      return bean;
    }
  }

  @Override
  public StatusBar findChild(Component c) {
    Component eachParent = c;
    IdeFrame frame = null;
    while (eachParent != null) {
      if (eachParent instanceof IdeFrame) {
        frame = (IdeFrame)eachParent;
      }
      eachParent = eachParent.getParent();
    }

    return frame != null ? frame.getStatusBar() : this;
  }

  private void updateChildren(@NotNull Consumer<? super IdeStatusBarImpl> consumer) {
    for (IdeStatusBarImpl child : myChildren) {
      consumer.accept(child);
    }
  }

  @NotNull
  @Override
  public StatusBar createChild(@NotNull IdeFrame frame) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    IdeStatusBarImpl bar = new IdeStatusBarImpl(frame, false);
    bar.setVisible(isVisible());
    myChildren.add(bar);
    Disposer.register(this, bar);
    Disposer.register(bar, () -> myChildren.remove(bar));

    for (WidgetBean eachBean : myWidgetMap.values()) {
      if (eachBean.widget instanceof StatusBarWidget.Multiframe) {
        StatusBarWidget copy = ((StatusBarWidget.Multiframe)eachBean.widget).copy();
        bar.addWidget(copy, eachBean.position, eachBean.anchor);
      }
    }
    bar.repaint();

    return bar;
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  @ApiStatus.Internal
  public IdeStatusBarImpl(@NotNull IdeFrame frame, boolean addToolWindowsWidget) {
    myFrame = frame;
    setLayout(new BorderLayout());
    setBorder(ExperimentalUI.isNewUI() ?
              JBUI.Borders.compound(JBUI.Borders.customLine(JBUI.CurrentTheme.StatusBar.BORDER_COLOR, 1, 0, 0, 0),
                                    JBUI.Borders.empty(0, 10, 1, 10)) :
              JBUI.Borders.empty(1, 0, 0, 6));

    myInfoAndProgressPanel = new InfoAndProgressPanel(UISettings.getShadowInstance());
    addWidget(myInfoAndProgressPanel, Position.CENTER, "__IGNORED__");
    Project project = myFrame.getProject();
    if (project != null) {
      project.getMessageBus().connect(this).subscribe(UISettingsListener.TOPIC, myInfoAndProgressPanel);
    }

    setOpaque(true);
    updateUI();

    if (addToolWindowsWidget) {
      addWidget(new ToolWindowsWidget(this), Position.LEFT, "__IGNORED__");
    }

    enableEvents(AWTEvent.MOUSE_EVENT_MASK);
    enableEvents(AWTEvent.MOUSE_MOTION_EVENT_MASK);
    IdeEventQueue.getInstance().addDispatcher(this, this);
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    if (size == null) return null;

    Insets insets = getInsets();
    int minHeight = insets.top + insets.bottom + MIN_ICON_HEIGHT;
    return new Dimension(size.width, Math.max(size.height, minHeight));
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    if (CommonDataKeys.PROJECT.is(dataId)) {
      return getProject();
    }
    if (PlatformDataKeys.STATUS_BAR.is(dataId)) {
      return this;
    }
    if (HOVERED_WIDGET_ID.is(dataId)) {
      return myEffectComponent instanceof JComponent ? ((JComponent)myEffectComponent).getClientProperty(WIDGET_ID) : null;
    }
    return null;
  }

  @Override
  public void setVisible(boolean aFlag) {
    super.setVisible(aFlag);
    for (IdeStatusBarImpl child : myChildren) {
      child.setVisible(aFlag);
    }
  }

  @Override
  public void addWidget(@NotNull StatusBarWidget widget) {
    addWidget(widget, "__AUTODETECT__");
  }

  @Override
  public void addWidget(@NotNull StatusBarWidget widget, @NotNull String anchor) {
    UIUtil.invokeLaterIfNeeded(() -> addWidget(widget, Position.RIGHT, anchor));
  }

  @Override
  public void addWidget(@NotNull final StatusBarWidget widget, @NotNull final Disposable parentDisposable) {
    addWidget(widget);
    String id = widget.ID();
    Disposer.register(parentDisposable, () -> removeWidget(id));
  }

  @Override
  public void addWidget(@NotNull final StatusBarWidget widget, @NotNull String anchor, @NotNull final Disposable parentDisposable) {
    addWidget(widget, anchor);
    String id = widget.ID();
    Disposer.register(parentDisposable, () -> removeWidget(id));
  }

  @ApiStatus.Experimental
  @Override
  public void setCentralWidget(@NotNull StatusBarCentralWidget widget) {
    UIUtil.invokeLaterIfNeeded(() -> addWidget(widget, Position.CENTER, ""));
  }

  /**
   * Adds widget to the left side of the status bar. Please note there is no hover effect when mouse is over the widget.
   * Use {@link #addWidget} to add widget to the right side of the status bar, in this case hover effect is on.
   * @param widget widget to add
   * @param parentDisposable when disposed, the widget will be removed from the status bar
   */
  public void addWidgetToLeft(@NotNull StatusBarWidget widget, @NotNull Disposable parentDisposable) {
    UIUtil.invokeLaterIfNeeded(() -> addWidget(widget, Position.LEFT, "__IGNORED__"));
    String id = widget.ID();
    Disposer.register(parentDisposable, () -> removeWidget(id));
  }

  @Override
  public void addCustomIndicationComponent(@NotNull final JComponent c) {
    final String customId = c.getClass().getName() + new Random().nextLong();
    addWidget(new CustomStatusBarWidget() {
      @Override
      @NotNull
      public String ID() {
        return customId;
      }

      @Override
      @Nullable
      public WidgetPresentation getPresentation() {
        return null;
      }

      @Override
      public void install(@NotNull StatusBar statusBar) {
      }

      @Override
      public void dispose() {
      }

      @Override
      public JComponent getComponent() {
        return c;
      }
    });

    myCustomComponentIds.add(customId);
  }

  @Override
  public void removeCustomIndicationComponent(@NotNull final JComponent c) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final Set<String> keySet = myWidgetMap.keySet();
    final String[] keys = ArrayUtilRt.toStringArray(keySet);
    for (final String key : keys) {
      final WidgetBean value = myWidgetMap.get(key);
      if (value.widget instanceof CustomStatusBarWidget && value.component == c) {
        removeWidget(key);
        myCustomComponentIds.remove(key);
      }
    }
  }

  @Override
  public void dispose() {
    removeCustomIndicationComponents();

    myWidgetMap.clear();
    myChildren.clear();

    if (myLeftPanel != null) myLeftPanel.removeAll();
    if (myRightPanel != null) myRightPanel.removeAll();
    if (myCenterPanel != null) myCenterPanel.removeAll();
  }

  private void removeCustomIndicationComponents() {
    for (final String id : myCustomComponentIds) {
      removeWidget(id);
    }
    myCustomComponentIds.clear();
  }

  private void addWidget(@NotNull StatusBarWidget widget, @NotNull Position position, @NotNull String anchor) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    JComponent c;
    JPanel panel;
    if (widget instanceof StatusBarCentralWidget && position == Position.CENTER) {
      c = ((StatusBarCentralWidget)widget).getCentralStatusBarComponent();
      myInfoAndProgressPanel.setCentralComponent(c);
      panel = myInfoAndProgressPanel;
    }
    else {
      c = wrap(widget);
      panel = getTargetPanel(position);
      if (position == Position.LEFT && panel.getComponentCount() == 0) {
        c.setBorder(SystemInfo.isMac ? JBUI.Borders.empty(2, 0, 2, 4) : JBUI.Borders.empty());
      }
      panel.add(c, getPositionIndex(position, anchor));

      if (c instanceof StatusBarWidgetWrapper) {
        ((StatusBarWidgetWrapper)c).beforeUpdate();
      }
    }

    myWidgetMap.put(widget.ID(), WidgetBean.create(widget, position, c, anchor));
    widget.install(this);
    panel.revalidate();

    Disposer.register(this, widget);
    fireWidgetAdded(widget, anchor);
    if (widget instanceof StatusBarWidget.Multiframe) {
      StatusBarWidget.Multiframe multiFrameWidget = (StatusBarWidget.Multiframe)widget;
      updateChildren(child -> child.addWidget(multiFrameWidget.copy(), position, anchor));
    }
  }

  private int getPositionIndex(@NotNull IdeStatusBarImpl.Position position, @NotNull String anchor) {
    if (Position.RIGHT == position && myRightPanel.getComponentCount() > 0) {
      WidgetBean widgetAnchor = null;
      boolean before = false;
      List<String> parts = StringUtil.split(anchor, " ");
      if (parts.size() > 1) {
        widgetAnchor = myWidgetMap.get(parts.get(1));
        before = "before".equalsIgnoreCase(parts.get(0));
      }
      if (widgetAnchor == null) {
        widgetAnchor = myWidgetMap.get(IdeNotificationArea.WIDGET_ID);
        if (widgetAnchor == null) {
          widgetAnchor = myWidgetMap.get(IdeMessagePanel.FATAL_ERROR);
        }
        before = true;
      }
      if (widgetAnchor != null) {
        int anchorIndex = ArrayUtil.indexOf(myRightPanel.getComponents(), widgetAnchor.component);
        return before ? anchorIndex : anchorIndex + 1;
      }
    }
    return -1;
  }

  @NotNull
  private JPanel getTargetPanel(@NotNull IdeStatusBarImpl.Position position) {
    if (position == Position.RIGHT) {
      return rightPanel();
    }
    if (position == Position.LEFT) {
      return leftPanel();
    }
    return centerPanel();
  }

  @NotNull
  private JPanel centerPanel() {
    if (myCenterPanel == null) {
      myCenterPanel = JBUI.Panels.simplePanel().andTransparent();
      myCenterPanel.setBorder(ExperimentalUI.isNewUI() ? JBUI.Borders.empty() : JBUI.Borders.empty(0, 1));
      add(myCenterPanel, BorderLayout.CENTER);
    }
    return myCenterPanel;
  }

  @NotNull
  private JPanel rightPanel() {
    if (myRightPanel == null) {
      myRightPanel = new JPanel();
      myRightPanel.setBorder(JBUI.Borders.emptyLeft(1));
      myRightPanel.setLayout(new BoxLayout(myRightPanel, BoxLayout.X_AXIS) {
        @Override
        public void layoutContainer(Container target) {
          super.layoutContainer(target);
          for (Component component : target.getComponents()) {
            if (component instanceof MemoryUsagePanel) {
              Rectangle r = component.getBounds();
              r.y = 0;
              r.width += SystemInfo.isMac ? 4 : 0;
              r.height = target.getHeight();
              component.setBounds(r);
            }
          }
        }
      });
      myRightPanel.setOpaque(false);
      add(myRightPanel, BorderLayout.EAST);
    }
    return myRightPanel;
  }

  @NotNull
  private JPanel leftPanel() {
    if (myLeftPanel == null) {
      myLeftPanel = new JPanel();
      myLeftPanel.setBorder(JBUI.Borders.empty(0, 4, 0, 1));
      myLeftPanel.setLayout(new BoxLayout(myLeftPanel, BoxLayout.X_AXIS));
      myLeftPanel.setOpaque(false);
      add(myLeftPanel, BorderLayout.WEST);
    }
    return myLeftPanel;
  }

  @Override
  public void setInfo(@Nullable final String s) {
    setInfo(s, null);
  }

  @Override
  public void setInfo(@Nullable @Nls String s, @Nullable String requestor) {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myInfoAndProgressPanel != null) {
        myInfo = myInfoAndProgressPanel.setText(s, requestor);
      }
    });
  }

  @Override
  @NlsContexts.StatusBarText
  public String getInfo() {
    return myInfo;
  }

  @Override
  public void addProgress(@NotNull ProgressIndicatorEx indicator, @NotNull TaskInfo info) {
    myInfoAndProgressPanel.addProgress(indicator, info);
  }

  @Override
  public List<Pair<TaskInfo, ProgressIndicator>> getBackgroundProcesses() {
    return myInfoAndProgressPanel.getBackgroundProcesses();
  }

  @Override
  public void setProcessWindowOpen(final boolean open) {
    myInfoAndProgressPanel.setProcessWindowOpen(open);
  }

  @Override
  public boolean isProcessWindowOpen() {
    return myInfoAndProgressPanel.isProcessWindowOpen();
  }

  @Override
  public void startRefreshIndication(final String tooltipText) {
    myInfoAndProgressPanel.setRefreshToolTipText(tooltipText);
    myInfoAndProgressPanel.setRefreshVisible(true);

    updateChildren(child -> child.startRefreshIndication(tooltipText));
  }

  @Override
  public void stopRefreshIndication() {
    myInfoAndProgressPanel.setRefreshVisible(false);

    updateChildren(IdeStatusBarImpl::stopRefreshIndication);
  }

  @Override
  public BalloonHandler notifyProgressByBalloon(@NotNull MessageType type, @NotNull @PopupContent String htmlBody) {
    return notifyProgressByBalloon(type, htmlBody, null, null);
  }

  @Override
  public BalloonHandler notifyProgressByBalloon(@NotNull MessageType type,
                                                @NotNull @PopupContent String htmlBody,
                                                @Nullable Icon icon,
                                                @Nullable HyperlinkListener listener) {
    return myInfoAndProgressPanel.notifyByBalloon(type, htmlBody, icon, listener);
  }

  @Override
  public void fireNotificationPopup(@NotNull JComponent content, Color backgroundColor) {
    new NotificationPopup(this, content, backgroundColor);
  }

  public static JComponent wrap(@NotNull final StatusBarWidget widget) {
    if (widget instanceof CustomStatusBarWidget) {
      JComponent component = ((CustomStatusBarWidget)widget).getComponent();
      if (component.getBorder() == null) {
        component.setBorder(widget instanceof IconLikeCustomStatusBarWidget ? JBUI.CurrentTheme.StatusBar.Widget.iconBorder()
                                                                            : JBUI.CurrentTheme.StatusBar.Widget.border());
      }
      // wrap with a panel, so it will fill entire status bar height
      JComponent result = component instanceof JLabel ? new NonOpaquePanel(new BorderLayout(), component) : component;
      result.putClientProperty(WIDGET_ID, widget.ID());
      return result;
    }

    StatusBarWidget.WidgetPresentation presentation = widget.getPresentation();
    if (presentation == null) {
      LOG.error("Widget " + widget + " getPresentation() method must not return null");
    }
    JComponent wrapper = StatusBarWidgetWrapper.wrap(Objects.requireNonNull(presentation));
    wrapper.putClientProperty(WIDGET_ID, widget.ID());
    wrapper.putClientProperty(UIUtil.CENTER_TOOLTIP_DEFAULT, Boolean.TRUE);
    return wrapper;
  }

  private void applyWidgetEffect(@Nullable JComponent component, @Nullable WidgetEffect widgetEffect) {
    if (myEffectComponent != component || myEffectComponent == null ||
        ComponentUtil.getClientProperty((JComponent)myEffectComponent, WIDGET_EFFECT_KEY) != widgetEffect) {

      if (myEffectComponent != null) {
        ComponentUtil.putClientProperty((JComponent)myEffectComponent, WIDGET_EFFECT_KEY, null);
      }

      myEffectComponent = component;
      // widgets shall not be opaque, as it may conflict with bg images
      // the following code can be dropped in future
      if (myEffectComponent != null) {
        myEffectComponent.setBackground(null);
        ComponentUtil.putClientProperty((JComponent)myEffectComponent, WIDGET_EFFECT_KEY, widgetEffect);
      }

      if (component != null && component.isEnabled() && widgetEffect != null) {
        component.setBackground(widgetEffect == WidgetEffect.HOVER ?
                                JBUI.CurrentTheme.StatusBar.Widget.HOVER_BACKGROUND:
                                JBUI.CurrentTheme.StatusBar.Widget.PRESSED_BACKGROUND);
      }
      repaint();
    }
  }

  private void paintWidgetEffectBackground(Graphics g) {
    if (myEffectComponent == null || !myEffectComponent.isEnabled()) return;
    if (!UIUtil.isAncestor(this, myEffectComponent)) return;
    if (myEffectComponent instanceof MemoryUsagePanel) return;

    Rectangle bounds = myEffectComponent.getBounds();
    Point point = new RelativePoint(myEffectComponent.getParent(), bounds.getLocation()).getPoint(this);

    var widgetEffect = ComponentUtil.getClientProperty((JComponent)myEffectComponent, WIDGET_EFFECT_KEY);
    var bg = widgetEffect == WidgetEffect.PRESSED ?
             JBUI.CurrentTheme.StatusBar.Widget.PRESSED_BACKGROUND :
             JBUI.CurrentTheme.StatusBar.Widget.HOVER_BACKGROUND;

    if (!ExperimentalUI.isNewUI() && getUI() instanceof StatusBarUI) {
      point.y += StatusBarUI.BORDER_WIDTH.get();
      bounds.height -= StatusBarUI.BORDER_WIDTH.get();
    }

    g.setColor(bg);
    g.fillRect(point.x, point.y, bounds.width, bounds.height);
  }

  @Override
  protected void paintChildren(Graphics g) {
    paintWidgetEffectBackground(g);
    super.paintChildren(g);
  }

  @Override
  public boolean dispatch(@NotNull AWTEvent e) {
    if (e instanceof MouseEvent) {
      return dispatchMouseEvent((MouseEvent)e);
    }
    return false;
  }

  private boolean dispatchMouseEvent(@NotNull MouseEvent e) {
    if (myRightPanel == null || myCenterPanel == null || !myRightPanel.isVisible()) {
      return false;
    }
    Component component = e.getComponent();
    if (component == null) {
      return false;
    }

    if (ComponentUtil.getWindow(myFrame.getComponent()) != ComponentUtil.getWindow(component)) {
      applyWidgetEffect(null, null);
      return false;
    }

    Point point = SwingUtilities.convertPoint(component, e.getPoint(), myRightPanel);
    Component widget = myRightPanel.getComponentAt(point);
    if (e.getClickCount() == 0 || e.getID() == MouseEvent.MOUSE_RELEASED) {
      applyWidgetEffect(widget != myRightPanel ? (JComponent)widget : null, WidgetEffect.HOVER);
    }
    else if (e.getClickCount() == 1 && e.getID() == MouseEvent.MOUSE_PRESSED) {
      applyWidgetEffect(widget != myRightPanel ? (JComponent)widget : null, WidgetEffect.PRESSED);
    }

    if (e.isConsumed() || widget == null) {
      return false;
    }
    if (e.isPopupTrigger() && (e.getID() == MouseEvent.MOUSE_PRESSED || e.getID() == MouseEvent.MOUSE_RELEASED)) {
      Project project = getProject();
      if (project != null) {
        ActionManager actionManager = ActionManager.getInstance();
        ActionGroup group = ObjectUtils.tryCast(actionManager.getAction(StatusBarWidgetsActionGroup.GROUP_ID), ActionGroup.class);
        if (group != null) {
          ActionPopupMenu menu = actionManager.createActionPopupMenu(ActionPlaces.STATUS_BAR_PLACE, group);
          menu.setTargetComponent(this);
          menu.getComponent().show(myRightPanel, point.x, point.y);
          e.consume();
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String getUIClassID() {
    return uiClassID;
  }

  @Override
  public void updateUI() {
    if (UIManager.get(getUIClassID()) != null) {
      setUI(UIManager.getUI(this));
    }
    else {
      setUI(new StatusBarUI());
    }
  }

  @Override
  protected Graphics getComponentGraphics(Graphics g) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(g));
  }

  @Override
  public void removeWidget(@NotNull String id) {
    UIUtil.invokeLaterIfNeeded(() -> {
      WidgetBean bean = myWidgetMap.remove(id);
      if (bean != null) {
        JPanel targetPanel = getTargetPanel(bean.position);
        targetPanel.remove(bean.component);
        targetPanel.revalidate();
        Disposer.dispose(bean.widget);
        fireWidgetRemoved(id);
      }
      updateChildren(child -> child.removeWidget(id));
    });
  }

  @Override
  public void updateWidget(@NotNull final String id) {
    UIUtil.invokeLaterIfNeeded(() -> {
      JComponent widgetComponent = getWidgetComponent(id);
      if (widgetComponent != null) {
        if (widgetComponent instanceof StatusBarWidgetWrapper) {
          ((StatusBarWidgetWrapper)widgetComponent).beforeUpdate();
        }
        widgetComponent.repaint();
        fireWidgetUpdated(id);
      }

      updateChildren(child -> child.updateWidget(id));
    });
  }

  @Override
  @Nullable
  public StatusBarWidget getWidget(String id) {
    WidgetBean bean = myWidgetMap.get(id);
    return bean == null ? null : bean.widget;
  }

  @Override
  public Collection<StatusBarWidget> getAllWidgets() {
    return ContainerUtil.map(myWidgetMap.values(), bean -> bean.widget);
  }

  @NonNls
  @Nullable
  @Override
  public String getWidgetAnchor(@NotNull String id) {
    WidgetBean bean = myWidgetMap.get(id);
    return bean == null ? null : bean.anchor;
  }

  @ApiStatus.Internal
  @Nullable
  //todo: make private after removing all external usages
  public JComponent getWidgetComponent(@NotNull String id) {
    WidgetBean bean = myWidgetMap.get(id);
    return bean == null ? null : bean.component;
  }

  @NotNull
  @Override
  public IdeFrame getFrame() {
    return myFrame;
  }

  @Nullable
  @Override
  public Project getProject() {
    return myFrame.getProject();
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleIdeStatusBarImpl();
    }
    return accessibleContext;
  }

  @Override
  public void addListener(@NotNull StatusBarListener listener, @NotNull Disposable parentDisposable) {
    myListeners.addListener(listener, parentDisposable);
  }

  private void fireWidgetAdded(@NotNull StatusBarWidget widget, @NonNls @Nullable String anchor) {
    myListeners.getMulticaster().widgetAdded(widget, anchor);
  }

  private void fireWidgetUpdated(@NonNls @NotNull String id) {
    myListeners.getMulticaster().widgetUpdated(id);
  }

  private void fireWidgetRemoved(@NonNls @NotNull String id) {
    myListeners.getMulticaster().widgetRemoved(id);
  }

  protected class AccessibleIdeStatusBarImpl extends AccessibleJComponent {
    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.PANEL;
    }
  }
}
