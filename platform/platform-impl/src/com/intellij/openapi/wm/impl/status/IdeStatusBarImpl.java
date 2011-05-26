/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.notification.Notifications;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.BalloonHandler;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.NotificationPopup;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.TimedDeadzone;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

/**
 * User: spLeaner
 */
public class IdeStatusBarImpl extends JComponent implements StatusBarEx {
  private InfoAndProgressPanel myInfoAndProgressPanel;
  private IdeFrame myFrame;

  private enum Position {LEFT, RIGHT, CENTER}

  private static final String uiClassID = "IdeStatusBarUI";

  private final Map<String, WidgetBean> myWidgetMap = new HashMap<String, WidgetBean>();
  private final List<String> myOrderedWidgets = new ArrayList<String>();

  private JPanel myLeftPanel;
  private JPanel myRightPanel;
  private JPanel myCenterPanel;

  private String myInfo;
  private String myRequestor;

  private final List<String> myCustomComponentIds = new ArrayList<String>();

  private final Set<IdeStatusBarImpl> myChildren = new HashSet<IdeStatusBarImpl>();

  private static class WidgetBean {
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

  @Override
  public void install(IdeFrame frame) {
    myFrame = frame;
  }

  private void updateChildren(ChildAction action) {
    for (IdeStatusBarImpl child : myChildren) {
      action.update(child);
    }
  }

  interface ChildAction {
    void update(IdeStatusBarImpl child);
  }

  public StatusBar createChild() {
    final IdeStatusBarImpl bar = new IdeStatusBarImpl(this);
    myChildren.add(bar);
    Disposer.register(bar, new Disposable() {
      @Override
      public void dispose() {
        myChildren.remove(bar);
      }
    });

    for (String eachId : myOrderedWidgets) {
      WidgetBean eachBean = myWidgetMap.get(eachId);
      if (eachBean.widget instanceof StatusBarWidget.Multiframe) {
        StatusBarWidget copy = ((StatusBarWidget.Multiframe)eachBean.widget).copy();
        bar.addWidget(copy, eachBean.position);
      }
    }

    return bar;
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  IdeStatusBarImpl(IdeStatusBarImpl master) {
    setLayout(new BorderLayout(2, 0));
    setBorder(BorderFactory.createEmptyBorder(1, 4, 0, SystemInfo.isMac ? 2 : 0));

    myInfoAndProgressPanel = new InfoAndProgressPanel(master == null);
    addWidget(myInfoAndProgressPanel, Position.CENTER);

    setOpaque(true);
    updateUI();

    if (master == null) {
      Disposer.register(Disposer.get("ui"), this);
    }

    if (master == null) {
      addWidget(new ToolWindowsWidget(), Position.LEFT);
    }
  }


  public IdeStatusBarImpl() {
    this(null);
  }

  public void addWidget(@NotNull final StatusBarWidget widget) {
    addWidget(widget, Position.RIGHT, "__AUTODETECT__");
  }

  public void addWidget(@NotNull final StatusBarWidget widget, @NotNull String anchor) {
    addWidget(widget, Position.RIGHT, anchor);
  }

  private void addWidget(@NotNull final StatusBarWidget widget, @NotNull final Position pos) {
    addWidget(widget, pos, "__IGNORED__");
  }

  public void addWidget(@NotNull final StatusBarWidget widget, @NotNull final Disposable parentDisposable) {
    addWidget(widget);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeWidget(widget.ID());
      }
    });
  }

  public void addWidget(@NotNull final StatusBarWidget widget, @NotNull String anchor, @NotNull final Disposable parentDisposable) {
    addWidget(widget, anchor);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeWidget(widget.ID());
      }
    });
  }

  public void removeCustomIndicationComponents() {
    for (final String id : myCustomComponentIds) {
      removeWidget(id);
    }

    myCustomComponentIds.clear();
  }

  public void addCustomIndicationComponent(@NotNull final JComponent c) {
    final String customId = c.getClass().getName() + new Random().nextLong();
    addWidget(new CustomStatusBarWidget() {
      @NotNull
      public String ID() {
        return customId;
      }

      @Nullable
      public WidgetPresentation getPresentation(@NotNull PlatformType type) {
        return null;
      }

      public void install(@NotNull StatusBar statusBar) {
      }

      public void dispose() {
      }

      public JComponent getComponent() {
        return c;
      }
    });

    myCustomComponentIds.add(customId);
  }

  public void removeCustomIndicationComponent(@NotNull final JComponent c) {
    final Set<String> keySet = myWidgetMap.keySet();
    final String[] keys = ArrayUtil.toStringArray(keySet);
    for (final String key : keys) {
      final WidgetBean value = myWidgetMap.get(key);
      if (value.widget instanceof CustomStatusBarWidget && value.component == c) {
        removeWidget(key);
        myCustomComponentIds.remove(key);
      }
    }
  }

  public void dispose() {
    myWidgetMap.clear();
  }

  private void addWidget(@NotNull final StatusBarWidget widget, @NotNull final Position pos, @NotNull final String anchor) {
    myOrderedWidgets.add(widget.ID());

    JPanel panel;
    if (pos == Position.RIGHT) {
      if (myRightPanel == null) {
        myRightPanel = new JPanel();
        myRightPanel.setLayout(new BoxLayout(myRightPanel, BoxLayout.X_AXIS));
        myRightPanel.setOpaque(false);
        add(myRightPanel, BorderLayout.EAST);
      }

      panel = myRightPanel;
    }
    else if (pos == Position.LEFT) {
      if (myLeftPanel == null) {
        myLeftPanel = new JPanel();
        myLeftPanel.setLayout(new BoxLayout(myLeftPanel, BoxLayout.X_AXIS));
        myLeftPanel.setOpaque(false);
        add(myLeftPanel, BorderLayout.WEST);
      }

      panel = myLeftPanel;
    }
    else {
      if (myCenterPanel == null) {
        myCenterPanel = new JPanel(new BorderLayout());
        myCenterPanel.setOpaque(false);
        add(myCenterPanel, BorderLayout.CENTER);
      }

      panel = myCenterPanel;
    }

    final JComponent c = wrap(widget);
    if (Position.RIGHT == pos && panel.getComponentCount() > 0) {
      String wid;
      boolean before;
      if (!anchor.equals("__AUTODETECT__")) {
        final List<String> parts = StringUtil.split(anchor, " ");
        if (parts.size() < 2 || !myWidgetMap.keySet().contains(parts.get(1))) {
          wid = "Notifications";
          before = true;
        }
        else {
          wid = parts.get(1);
          before = "before".equalsIgnoreCase(parts.get(0));
        }
      }
      else {
        wid = "Notifications";
        before = true;
      }

      for (final String id : myWidgetMap.keySet()) {
        if (id.equalsIgnoreCase(wid)) {
          final WidgetBean bean = myWidgetMap.get(id);
          int i = 0;
          for (final Component component : myRightPanel.getComponents()) {
            if (component == bean.component) {
              if (before) {
                panel.add(c, i);
                updateBorder(i);
              }
              else {
                final int ndx = i + 1;
                panel.add(c, i + 1);
                updateBorder(ndx);
              }

              installWidget(widget, pos, c, anchor);
              return;
            }

            i++;
          }
        }
      }
    }

    if (Position.LEFT == pos && panel.getComponentCount() == 0) {
      c.setBorder(SystemInfo.isMac ? BorderFactory.createEmptyBorder(2, 0, 2, 4) : BorderFactory.createEmptyBorder());
    }

    panel.add(c);
    installWidget(widget, pos, c, anchor);

    if (widget instanceof StatusBarWidget.Multiframe) {
      final StatusBarWidget.Multiframe mfw = (StatusBarWidget.Multiframe)widget;
      updateChildren(new ChildAction() {
        @Override
        public void update(IdeStatusBarImpl child) {
          StatusBarWidget widgetCopy = mfw.copy();
          child.addWidget(widgetCopy, pos, anchor);
        }
      });
    }
  }

  private void updateBorder(final int ndx) {
    final JComponent self = (JComponent)myRightPanel.getComponent(ndx);
    if (self instanceof IconPresentationWrapper) {
      final int prev = ndx - 1;
      final int next = ndx + 1;

      final JComponent p = prev >= 0 ? (JComponent)myRightPanel.getComponent(prev) : null;
      final JComponent n = next < myRightPanel.getComponentCount() ? (JComponent)myRightPanel.getComponent(next) : null;

      final boolean prevIcon = p instanceof IconPresentationWrapper;
      final boolean nextIcon = n instanceof IconPresentationWrapper;

      self.setBorder(prevIcon ? BorderFactory.createEmptyBorder(2, 2, 2, 2) : StatusBarWidget.WidgetBorder.INSTANCE);
      if (nextIcon) n.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    }
  }

  public void setInfo(@Nullable final String s) {
    setInfo(s, null);
  }

  @Override
  public void setInfo(@Nullable final String s, @Nullable final String requestor) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        if (myInfoAndProgressPanel != null) {
          Pair<String, String> pair = myInfoAndProgressPanel.setText(s, requestor);
          myInfo = pair.first;
          myRequestor = pair.second;
        }
      }
    });
  }

  public String getInfo() {
    return myInfo;
  }

  @Override
  public String getInfoRequestor() {
    return myRequestor;
  }

  public void addProgress(ProgressIndicatorEx indicator, TaskInfo info) {
    myInfoAndProgressPanel.addProgress(indicator, info);
  }

  public void setProcessWindowOpen(final boolean open) {
    myInfoAndProgressPanel.setProcessWindowOpen(open);
  }

  public boolean isProcessWindowOpen() {
    return myInfoAndProgressPanel.isProcessWindowOpen();
  }

  public void startRefreshIndication(final String tooltipText) {
    myInfoAndProgressPanel.setRefreshToolTipText(tooltipText);
    myInfoAndProgressPanel.setRefreshVisible(true);

    updateChildren(new ChildAction() {
      @Override
      public void update(IdeStatusBarImpl child) {
        child.startRefreshIndication(tooltipText);
      }
    });
  }

  public void stopRefreshIndication() {
    myInfoAndProgressPanel.setRefreshVisible(false);

    updateChildren(new ChildAction() {
      @Override
      public void update(IdeStatusBarImpl child) {
        child.stopRefreshIndication();
      }
    });
  }

  public BalloonHandler notifyProgressByBalloon(@NotNull MessageType type, @NotNull String htmlBody) {
    return notifyProgressByBalloon(type, htmlBody, null, null);
  }

  public BalloonHandler notifyProgressByBalloon(@NotNull MessageType type,
                                                @NotNull String htmlBody,
                                                @Nullable Icon icon,
                                                @Nullable HyperlinkListener listener) {
    Notifications.Bus.notify(ToolWindowManagerImpl.createNotification(type, htmlBody, listener), myFrame.getProject());

    if (NotificationsManagerImpl.isEventLogVisible(myFrame.getProject())) {
      return new BalloonHandler() {
        @Override
        public void hide() {
        }
      };
    }

    return myInfoAndProgressPanel.notifyByBalloon(type, htmlBody, icon, listener);
  }

  public void fireNotificationPopup(@NotNull JComponent content, Color backgroundColor) {
    new NotificationPopup(this, content, backgroundColor);
  }

  private void installWidget(@NotNull final StatusBarWidget widget,
                             @NotNull final Position pos,
                             @NotNull final JComponent c,
                             String anchor) {
    myWidgetMap.put(widget.ID(), WidgetBean.create(widget, pos, c, anchor));
    widget.install(this);
    Disposer.register(this, widget);
  }

  private static JComponent wrap(@NotNull final StatusBarWidget widget) {
    if (widget instanceof CustomStatusBarWidget) return ((CustomStatusBarWidget)widget).getComponent();
    final StatusBarWidget.WidgetPresentation presentation =
      widget.getPresentation(SystemInfo.isMac ? StatusBarWidget.PlatformType.MAC : StatusBarWidget.PlatformType.DEFAULT);
    assert presentation != null : "Presentation should not be null!";

    JComponent wrapper;
    if (presentation instanceof StatusBarWidget.IconPresentation) {
      wrapper = new IconPresentationWrapper((StatusBarWidget.IconPresentation)presentation);
    }
    else if (presentation instanceof StatusBarWidget.TextPresentation) {
      wrapper = new TextPresentationWrapper((StatusBarWidget.TextPresentation)presentation);
      wrapper.setBorder(StatusBarWidget.WidgetBorder.INSTANCE);
    }
    else if (presentation instanceof StatusBarWidget.MultipleTextValuesPresentation) {
      wrapper = new MultipleTextValuesPresentationWrapper((StatusBarWidget.MultipleTextValuesPresentation)presentation);
      wrapper.setBorder(StatusBarWidget.WidgetBorder.INSTANCE);
    }
    else {
      throw new IllegalArgumentException("Unable to find a wrapper for presentation: " + presentation.getClass().getSimpleName());
    }

    return wrapper;
  }

  public String getUIClassID() {
    return uiClassID;
  }

  @SuppressWarnings({"MethodOverloadsMethodOfSuperclass"})
  protected void setUI(StatusBarUI ui) {
    super.setUI(ui);
  }

  @Override
  public void updateUI() {
    if (UIManager.get(getUIClassID()) != null) {
      setUI((StatusBarUI)UIManager.getUI(this));
    }
    else {
      setUI(SystemInfo.isMac ? new MacStatusBarUI() : new StatusBarUI());
    }
  }

  @Override
  protected void paintChildren(final Graphics g) {
    if (getUI() instanceof MacStatusBarUI && !MacStatusBarUI.isActive(this)) {
      final Graphics2D g2d = (Graphics2D)g.create();
      //g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.4f));
      super.paintChildren(g2d);
      g2d.dispose();
    }
    else {
      super.paintChildren(g);
    }
  }

  public StatusBarUI getUI() {
    return (StatusBarUI)ui;
  }

  public void removeWidget(@NotNull final String id) {
    final WidgetBean bean = myWidgetMap.get(id);
    if (bean != null) {
      if (Position.LEFT == bean.position) {
        myLeftPanel.remove(bean.component);
      }
      else if (Position.RIGHT == bean.position) {
        final Component[] components = myRightPanel.getComponents();
        int i = 0;
        for (final Component c : components) {
          if (c == bean.component) break;
          i++;
        }

        myRightPanel.remove(bean.component);
        updateBorder(i);
      }
      else {
        myCenterPanel.remove(bean.component);
      }

      myWidgetMap.remove(bean.widget.ID());
      Disposer.dispose(bean.widget);
    }

    updateChildren(new ChildAction() {
      @Override
      public void update(IdeStatusBarImpl child) {
        child.removeWidget(id);
      }
    });

    myOrderedWidgets.remove(id);
  }

  public void updateWidgets() {
    for (final String s : myWidgetMap.keySet()) {
      updateWidget(s);
    }

    updateChildren(new ChildAction() {
      @Override
      public void update(IdeStatusBarImpl child) {
        child.updateWidgets();
      }
    });
  }

  public void updateWidget(@NotNull final String id) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        final WidgetBean bean = myWidgetMap.get(id);
        if (bean != null) {
          if (bean.component instanceof StatusBarWrapper) {
            ((StatusBarWrapper)bean.component).beforeUpdate();
          }

          bean.component.repaint();
        }

        updateChildren(new ChildAction() {
          @Override
          public void update(IdeStatusBarImpl child) {
            child.updateWidget(id);
          }
        });
      }
    });
  }

  private interface StatusBarWrapper {
    void beforeUpdate();
  }

  private static final class MultipleTextValuesPresentationWrapper extends TextPanel implements StatusBarWrapper {
    private static final Icon ARROWS_ICON = IconLoader.getIcon("/ide/statusbar_arrows.png");
    private final StatusBarWidget.MultipleTextValuesPresentation myPresentation;

    private MultipleTextValuesPresentationWrapper(@NotNull final StatusBarWidget.MultipleTextValuesPresentation presentation) {
      super(presentation.getMaxValue());
      myPresentation = presentation;

      putClientProperty(UIUtil.CENTER_TOOLTIP_DEFAULT, Boolean.TRUE);
      setToolTipText(presentation.getTooltipText());

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(final MouseEvent e) {
          final ListPopup popup = myPresentation.getPopupStep();
          if (popup == null) return;
          final Dimension dimension = popup.getContent().getPreferredSize();
          final Point at = new Point(0, -dimension.height);
          popup.show(new RelativePoint(e.getComponent(), at));
        }
      });

      setOpaque(false);
    }

    public void beforeUpdate() {
      setText(myPresentation.getSelectedValue());
    }

    @Override
    @Nullable
    public String getToolTipText() {
      return myPresentation.getTooltipText();
    }

    @Override
    protected void paintComponent(@NotNull final Graphics g) {
      super.paintComponent(g);

      if (getText() != null) {
        final Rectangle r = getBounds();
        final Insets insets = getInsets();
        ARROWS_ICON
          .paintIcon(this, g, r.width - insets.right - ARROWS_ICON.getIconWidth() - 2, r.height / 2 - ARROWS_ICON.getIconHeight() / 2);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      final Dimension preferredSize = super.getPreferredSize();
      return new Dimension(preferredSize.width + ARROWS_ICON.getIconWidth() + 4, preferredSize.height);
    }
  }

  private static final class TextPresentationWrapper extends TextPanel implements StatusBarWrapper {
    private final StatusBarWidget.TextPresentation myPresentation;
    private final Consumer<MouseEvent> myClickConsumer;
    private boolean myMouseOver;

    private TextPresentationWrapper(@NotNull final StatusBarWidget.TextPresentation presentation) {
      super(presentation.getMaxPossibleText());
      myPresentation = presentation;
      myClickConsumer = myPresentation.getClickConsumer();

      setTextAlignment(presentation.getAlignment());

      putClientProperty(UIUtil.CENTER_TOOLTIP_DEFAULT, Boolean.TRUE);
      setToolTipText(presentation.getTooltipText());

      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(final MouseEvent e) {
          if (myClickConsumer != null && !e.isPopupTrigger() && MouseEvent.BUTTON1 == e.getButton()) {
            myClickConsumer.consume(e);
          }
        }

        @Override
        public void mouseEntered(MouseEvent e) {
          myMouseOver = true;
        }

        @Override
        public void mouseExited(MouseEvent e) {
          myMouseOver = false;
        }
      });

      setOpaque(false);
    }

    @Override
    @Nullable
    public String getToolTipText() {
      return myPresentation.getTooltipText();
    }

    public void beforeUpdate() {
      setText(myPresentation.getText());
    }
  }

  private static final class IconPresentationWrapper extends JComponent implements StatusBarWrapper {
    private final StatusBarWidget.IconPresentation myPresentation;
    private final Consumer<MouseEvent> myClickConsumer;
    private Icon myIcon;

    private IconPresentationWrapper(@NotNull final StatusBarWidget.IconPresentation presentation) {
      myPresentation = presentation;
      myClickConsumer = myPresentation.getClickConsumer();
      myIcon = myPresentation.getIcon();

      putClientProperty(UIUtil.CENTER_TOOLTIP_DEFAULT, Boolean.TRUE);
      setToolTipText(presentation.getTooltipText());

      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(final MouseEvent e) {
          if (myClickConsumer != null && !e.isPopupTrigger() && MouseEvent.BUTTON1 == e.getButton()) {
            myClickConsumer.consume(e);
          }
        }
      });

      setOpaque(false);
    }

    public void beforeUpdate() {
      myIcon = myPresentation.getIcon();
    }

    @Override
    @Nullable
    public String getToolTipText() {
      return myPresentation.getTooltipText();
    }

    @Override
    protected void paintComponent(final Graphics g) {
      final Rectangle bounds = getBounds();
      final Insets insets = getInsets();

      if (myIcon != null) {
        final int iconWidth = myIcon.getIconWidth();
        final int iconHeight = myIcon.getIconHeight();

        myIcon.paintIcon(this, g, insets.left + (bounds.width - insets.left - insets.right - iconWidth) / 2,
                         insets.top + (bounds.height - insets.top - insets.bottom - iconHeight) / 2);
      }
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(24, 18);
    }

    @Override
    public Dimension getPreferredSize() {
      return getMinimumSize();
    }
  }

  @Override
  public IdeFrame getFrame() {
    return myFrame;
  }

  private static class ToolWindowsWidget extends JLabel implements CustomStatusBarWidget, StatusBarWidget, Disposable,
                                                                   UISettingsListener, PropertyChangeListener {

    private static final Icon HIDDEN = IconLoader.getIcon("/general/tbShown.png");
    private static final Icon SHOWN = IconLoader.getIcon("/general/tbHidden.png");
    private StatusBar myStatusBar;

    private ToolWindowsWidget() {
      new BaseButtonBehavior(this, TimedDeadzone.NULL) {
        @Override
        protected void execute(MouseEvent e) {
          performAction();
        }
      }.setActionTrigger(MouseEvent.MOUSE_PRESSED);

      UISettings.getInstance().addUISettingsListener(this, this);
      KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", this);
    }


    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      updateIcon();
    }

    @Override
    public void uiSettingsChanged(UISettings source) {
      updateIcon();
    }

    private void performAction() {
      if (isActive()) {
        UISettings.getInstance().HIDE_TOOL_STRIPES = !UISettings.getInstance().HIDE_TOOL_STRIPES;
        UISettings.getInstance().fireUISettingsChanged();
      }
    }

    private void updateIcon() {
      if (isActive()) {
        boolean changes = false;

        if (!isVisible()) {
          setVisible(true);
          changes = true;
        }

        Icon icon = UISettings.getInstance().HIDE_TOOL_STRIPES ? HIDDEN : SHOWN;
        if (icon != getIcon()) {
          setIcon(icon);
          changes = true;
        }

        Set<Integer> vks = ToolWindowManagerImpl.getActivateToolWindowVKs();
        String text = "Click to show/hide tool windows side bars";
        if (vks.size() == 1) {
          Integer stroke = vks.iterator().next();
          String keystrokeText = KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(stroke.intValue(), 0));
          text += ".\nUse double click of " + keystrokeText + " to popup them when hidden";
        }
        if (!text.equals(getToolTipText())) {
          setToolTipText(text);
          changes = true;
        }

        if (changes) {
          revalidate();
          repaint();
        }
      }
      else {
        setVisible(false);
        setToolTipText(null);
      }
    }

    private boolean isActive() {
      return myStatusBar != null && myStatusBar.getFrame() != null && myStatusBar.getFrame().getProject() != null && Registry.is("ide.windowSystem.showTooWindowButtonsSwitcher");
    }

    @Override
    public JComponent getComponent() {
      return this;
    }

    @NotNull
    @Override
    public String ID() {
      return "ToolWindows Widget";
    }

    @Override
    public WidgetPresentation getPresentation(@NotNull PlatformType type) {
      return null;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
      myStatusBar = statusBar;
      updateIcon();
    }

    @Override
    public void dispose() {
      Disposer.dispose(this);
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusOwner", this);
      myStatusBar = null;
    }
  }
}
