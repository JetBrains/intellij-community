// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.HelpTooltipManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.notification.impl.IdeNotificationArea;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.BalloonHandler;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.ui.ClickListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.NotificationPopup;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

public final class IdeStatusBarImpl extends JComponent implements Accessible, StatusBarEx, IdeEventQueue.EventDispatcher {
  private static final int MIN_ICON_HEIGHT = 18 + 1 + 1;
  private final InfoAndProgressPanel myInfoAndProgressPanel;
  @NotNull
  private final IdeFrame myFrame;

  private enum Position {LEFT, RIGHT, CENTER}

  private static final String uiClassID = "IdeStatusBarUI";

  private final Map<String, WidgetBean> myWidgetMap = new HashMap<>();
  private final List<String> myOrderedWidgets = new ArrayList<>();

  private JPanel myLeftPanel;
  private JPanel myRightPanel;
  private JPanel myCenterPanel;
  private Component myHoveredComponent;

  private String myInfo;

  private final List<String> myCustomComponentIds = new ArrayList<>();

  private final Set<IdeStatusBarImpl> myChildren = new THashSet<>();

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

  private void updateChildren(@NotNull Consumer<IdeStatusBarImpl> consumer) {
    for (IdeStatusBarImpl child : myChildren) {
      consumer.accept(child);
    }
  }

  @NotNull
  @Override
  public StatusBar createChild(@NotNull IdeFrame frame) {
    IdeStatusBarImpl bar = new IdeStatusBarImpl(frame, false);
    bar.setVisible(isVisible());
    myChildren.add(bar);
    Disposer.register(this, bar);
    Disposer.register(bar, () -> myChildren.remove(bar));

    for (String eachId : myOrderedWidgets) {
      WidgetBean eachBean = myWidgetMap.get(eachId);
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

  private IdeStatusBarImpl(@NotNull IdeFrame frame, boolean addToolWindowsWidget) {
    myFrame = frame;
    setLayout(new BorderLayout());
    setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 6));

    myInfoAndProgressPanel = new InfoAndProgressPanel();
    addWidget(myInfoAndProgressPanel, Position.CENTER, "__IGNORED__");

    setOpaque(true);
    updateUI();

    if (addToolWindowsWidget) {
      addWidget(new ToolWindowsWidget(this), Position.LEFT, "__IGNORED__");
    }

    enableEvents(AWTEvent.MOUSE_EVENT_MASK);
    enableEvents(AWTEvent.MOUSE_MOTION_EVENT_MASK);
    IdeEventQueue.getInstance().addPostprocessor(this, this);
  }

  public IdeStatusBarImpl(@NotNull IdeFrame frame) {
    this(frame, true);
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
    Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      addWidget(widget, Position.RIGHT, anchor);
      repaint();
    }
    else {
      app.invokeLater(() -> {
        addWidget(widget, Position.RIGHT, anchor);
        repaint();
      });
    }
  }

  @Override
  public void addWidget(@NotNull final StatusBarWidget widget, @NotNull final Disposable parentDisposable) {
    addWidget(widget);
    Disposer.register(parentDisposable, () -> removeWidget(widget.ID()));
  }

  @Override
  public void addWidget(@NotNull final StatusBarWidget widget, @NotNull String anchor, @NotNull final Disposable parentDisposable) {
    addWidget(widget, anchor);
    Disposer.register(parentDisposable, () -> removeWidget(widget.ID()));
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

  @ApiStatus.Internal
  public void doAddWidget(@NotNull StatusBarWidget widget, @NotNull String anchor) {
    addWidget(widget, Position.RIGHT, anchor);
  }

  private void addWidget(@NotNull StatusBarWidget widget, @NotNull Position pos, @NotNull String anchor) {
    myOrderedWidgets.add(widget.ID());

    JPanel panel;
    if (pos == Position.RIGHT) {
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

      panel = myRightPanel;
    }
    else if (pos == Position.LEFT) {
      if (myLeftPanel == null) {
        myLeftPanel = new JPanel();
        myLeftPanel.setBorder(JBUI.Borders.empty(0, 4, 0, 1));
        myLeftPanel.setLayout(new BoxLayout(myLeftPanel, BoxLayout.X_AXIS));
        myLeftPanel.setOpaque(false);
        add(myLeftPanel, BorderLayout.WEST);
      }

      panel = myLeftPanel;
    }
    else {
      if (myCenterPanel == null) {
        myCenterPanel = JBUI.Panels.simplePanel().andTransparent();
        myCenterPanel.setBorder(JBUI.Borders.empty(0, 1));
        add(myCenterPanel, BorderLayout.CENTER);
      }

      panel = myCenterPanel;
    }

    JComponent c = wrap(widget);
    if (Position.RIGHT == pos && panel.getComponentCount() > 0) {
      String widgetId;
      boolean before;
      if (anchor.equals("__AUTODETECT__")) {
        widgetId = IdeNotificationArea.WIDGET_ID;
        before = true;
      }
      else {
        final List<String> parts = StringUtil.split(anchor, " ");
        if (parts.size() < 2 || !myWidgetMap.containsKey(parts.get(1))) {
          widgetId = IdeNotificationArea.WIDGET_ID;
          before = true;
        }
        else {
          widgetId = parts.get(1);
          before = "before".equalsIgnoreCase(parts.get(0));
        }
      }

      for (String id : myWidgetMap.keySet()) {
        if (!id.equalsIgnoreCase(widgetId)) {
          continue;
        }

        WidgetBean bean = myWidgetMap.get(id);
        int i = 0;
        for (Component component : myRightPanel.getComponents()) {
          if (component == bean.component) {
            if (before) {
              panel.add(c, i);
            }
            else {
              panel.add(c, i + 1);
            }

            installWidget(widget, pos, c, anchor);
            return;
          }

          i++;
        }
      }
    }

    if (Position.LEFT == pos && panel.getComponentCount() == 0) {
      c.setBorder(SystemInfo.isMac ? JBUI.Borders.empty(2, 0, 2, 4) : JBUI.Borders.empty());
    }

    panel.add(c);
    installWidget(widget, pos, c, anchor);

    if (widget instanceof StatusBarWidget.Multiframe) {
      StatusBarWidget.Multiframe multiFrameWidget = (StatusBarWidget.Multiframe)widget;
      updateChildren(child -> child.addWidget(multiFrameWidget.copy(), pos, anchor));
    }
  }

  @Override
  public void setInfo(@Nullable final String s) {
    setInfo(s, null);
  }

  @Override
  public void setInfo(@Nullable final String s, @Nullable final String requestor) {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myInfoAndProgressPanel != null) {
        Couple<String> pair = myInfoAndProgressPanel.setText(s, requestor);
        myInfo = pair.first;
      }
    });
  }

  @Override
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
  public BalloonHandler notifyProgressByBalloon(@NotNull MessageType type, @NotNull String htmlBody) {
    return notifyProgressByBalloon(type, htmlBody, null, null);
  }

  @Override
  public BalloonHandler notifyProgressByBalloon(@NotNull MessageType type,
                                                @NotNull String htmlBody,
                                                @Nullable Icon icon,
                                                @Nullable HyperlinkListener listener) {
    return myInfoAndProgressPanel.notifyByBalloon(type, htmlBody, icon, listener);
  }

  @Override
  public void fireNotificationPopup(@NotNull JComponent content, Color backgroundColor) {
    new NotificationPopup(this, content, backgroundColor);
  }

  private void installWidget(@NotNull StatusBarWidget widget,
                             @NotNull Position pos,
                             @NotNull JComponent c,
                             @NotNull String anchor) {
    myWidgetMap.put(widget.ID(), WidgetBean.create(widget, pos, c, anchor));
    widget.install(this);
    Disposer.register(this, widget);
  }

  private static JComponent wrap(@NotNull final StatusBarWidget widget) {
    if (widget instanceof CustomStatusBarWidget) {
      JComponent component = ((CustomStatusBarWidget)widget).getComponent();
      if (component.getBorder() == null) {
        component.setBorder(widget instanceof IconLikeCustomStatusBarWidget ? StatusBarWidget.WidgetBorder.ICON
                                                                            : StatusBarWidget.WidgetBorder.INSTANCE);
      }
      if (component instanceof JLabel) {
        // wrap with panel so it will fill entire status bar height
        return UI.Panels.simplePanel(component);
      }
      return component;
    }
    StatusBarWidget.WidgetPresentation presentation = widget.getPresentation();
    assert presentation != null : "Presentation should not be null!";

    JComponent wrapper;
    if (presentation instanceof StatusBarWidget.IconPresentation) {
      wrapper = new IconPresentationWrapper((StatusBarWidget.IconPresentation)presentation);
      wrapper.setBorder(StatusBarWidget.WidgetBorder.ICON);
    }
    else if (presentation instanceof StatusBarWidget.TextPresentation) {
      wrapper = new TextPresentationWrapper((StatusBarWidget.TextPresentation)presentation);
      wrapper.setBorder(StatusBarWidget.WidgetBorder.INSTANCE);
    }
    else if (presentation instanceof StatusBarWidget.MultipleTextValuesPresentation) {
      wrapper = new MultipleTextValuesPresentationWrapper((StatusBarWidget.MultipleTextValuesPresentation)presentation);
      wrapper.setBorder(StatusBarWidget.WidgetBorder.WIDE);
    }
    else {
      throw new IllegalArgumentException("Unable to find a wrapper for presentation: " + presentation.getClass().getSimpleName());
    }
    wrapper.putClientProperty(UIUtil.CENTER_TOOLTIP_DEFAULT, Boolean.TRUE);
    return wrapper;
  }

  private void hoverComponent(@Nullable Component component) {
    if (myHoveredComponent != null) {
      myHoveredComponent.setBackground(null);
    }
    if (component != null && component.isEnabled()) {
      component.setBackground(JBUI.CurrentTheme.StatusBar.hoverBackground());
    }
    myHoveredComponent = component;
  }

  @Override
  public boolean dispatch(@NotNull AWTEvent e) {
    if (e instanceof MouseEvent) {
      dispatchMouseEvent((MouseEvent)e);
    }
    return false;
  }

  private void dispatchMouseEvent(@NotNull MouseEvent e) {
    if (myRightPanel == null) {
      return;
    }
    Component component = e.getComponent();
    if (component == null) {
      return;
    }
    Point point = SwingUtilities.convertPoint(component, e.getPoint(), myRightPanel);
    Component widget = myRightPanel.getComponentAt(point);
    hoverComponent(widget != myRightPanel ? widget : null);
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

  public StatusBarUI getUI() {
    return (StatusBarUI)ui;
  }

  @Override
  public void removeWidget(@NotNull String id) {
    assert EventQueue.isDispatchThread() : "Must be EDT";
    final WidgetBean bean = myWidgetMap.get(id);
    if (bean != null) {
      if (Position.LEFT == bean.position) {
        myLeftPanel.remove(bean.component);
      }
      else if (Position.RIGHT == bean.position) {
        final Component[] components = myRightPanel.getComponents();
        for (final Component c : components) {
          if (c == bean.component) break;
        }

        myRightPanel.remove(bean.component);
      }
      else {
        myCenterPanel.remove(bean.component);
      }

      myWidgetMap.remove(bean.widget.ID());
      Disposer.dispose(bean.widget);

      repaint();
    }

    updateChildren(child -> child.removeWidget(id));

    myOrderedWidgets.remove(id);
  }

  @Override
  public void updateWidgets() {
    for (final String s : myWidgetMap.keySet()) {
      updateWidget(s);
    }

    updateChildren(IdeStatusBarImpl::updateWidgets);
  }

  @Override
  public void updateWidget(@NotNull final String id) {
    UIUtil.invokeLaterIfNeeded(() -> {
      final WidgetBean bean = myWidgetMap.get(id);
      if (bean != null) {
        if (bean.component instanceof StatusBarWrapper) {
          ((StatusBarWrapper)bean.component).beforeUpdate();
        }

        bean.component.repaint();
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

  @Nullable
  public JComponent getWidgetComponent(@NotNull String id) {
    WidgetBean bean = myWidgetMap.get(id);
    return bean == null ? null : bean.component;
  }

  @FunctionalInterface
  private interface StatusBarWrapper {
    void beforeUpdate();
  }

  private static void initTooltip(JComponent component, StatusBarWidget.WidgetPresentation presentation) {
    component.setToolTipText(presentation.getTooltipText());

    if (Registry.is("ide.helptooltip.enabled")) {
      component.putClientProperty(HelpTooltipManager.SHORTCUT_PROPERTY, presentation.getShortcutText());
    }
  }

  private static final class MultipleTextValuesPresentationWrapper extends TextPanel.WithIconAndArrows implements StatusBarWrapper {
    private final StatusBarWidget.MultipleTextValuesPresentation myPresentation;

    private MultipleTextValuesPresentationWrapper(@NotNull final StatusBarWidget.MultipleTextValuesPresentation presentation) {
      myPresentation = presentation;
      setVisible(StringUtil.isNotEmpty(myPresentation.getSelectedValue()));
      setTextAlignment(Component.CENTER_ALIGNMENT);
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent e, int clickCount) {
          final ListPopup popup = myPresentation.getPopupStep();
          if (popup == null) return false;
          final Dimension dimension = popup.getContent().getPreferredSize();
          final Point at = new Point(0, -dimension.height);
          popup.show(new RelativePoint(e.getComponent(), at));
          return true;
        }
      }.installOn(this);
    }

    @Override
    public Font getFont() {
      return SystemInfo.isMac ? JBUI.Fonts.label(11) : JBFont.label();
    }

    @Override
    public void beforeUpdate() {
      String value = myPresentation.getSelectedValue();
      setText(value);
      setIcon(myPresentation.getIcon());
      setVisible(StringUtil.isNotEmpty(value));
      initTooltip(this, myPresentation);
    }
  }

  private static final class TextPresentationWrapper extends TextPanel implements StatusBarWrapper {
    private final StatusBarWidget.TextPresentation myPresentation;
    private final com.intellij.util.Consumer<MouseEvent> myClickConsumer;

    private TextPresentationWrapper(@NotNull final StatusBarWidget.TextPresentation presentation) {
      myPresentation = presentation;
      myClickConsumer = myPresentation.getClickConsumer();
      setTextAlignment(presentation.getAlignment());
      setVisible(!myPresentation.getText().isEmpty());
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(final MouseEvent e) {
          if (myClickConsumer != null && !e.isPopupTrigger() && MouseEvent.BUTTON1 == e.getButton()) {
            myClickConsumer.consume(e);
          }
        }
      });
    }

    @Override
    public void beforeUpdate() {
      String text = myPresentation.getText();
      setText(text);
      setVisible(!text.isEmpty());
      initTooltip(this, myPresentation);
    }
  }

  private static final class IconPresentationWrapper extends TextPanel.WithIconAndArrows implements StatusBarWrapper {
    private final StatusBarWidget.IconPresentation myPresentation;
    private final com.intellij.util.Consumer<MouseEvent> myClickConsumer;

    private IconPresentationWrapper(@NotNull final StatusBarWidget.IconPresentation presentation) {
      myPresentation = presentation;
      myClickConsumer = myPresentation.getClickConsumer();
      setTextAlignment(Component.CENTER_ALIGNMENT);
      setIcon(myPresentation.getIcon());
      setVisible(hasIcon());
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(final MouseEvent e) {
          if (myClickConsumer != null && !e.isPopupTrigger() && MouseEvent.BUTTON1 == e.getButton()) {
            myClickConsumer.consume(e);
          }
        }
      });
    }

    @Override
    public void beforeUpdate() {
      setIcon(myPresentation.getIcon());
      setVisible(hasIcon());
      initTooltip(this, myPresentation);
    }
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

  protected class AccessibleIdeStatusBarImpl extends AccessibleJComponent {
    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.PANEL;
    }
  }
}
