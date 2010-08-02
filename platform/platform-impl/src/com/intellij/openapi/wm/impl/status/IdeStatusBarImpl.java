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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.BalloonHandler;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.NotificationPopup;
import com.intellij.util.Consumer;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * User: spLeaner
 */
public class IdeStatusBarImpl extends JComponent implements StatusBarEx {
  private InfoAndProgressPanel myInfoAndProgressPanel;

  private enum Position {LEFT, RIGHT, CENTER}

  private static final String uiClassID = "IdeStatusBarUI";

  private final Map<String, WidgetBean> myWidgetMap = new HashMap<String, WidgetBean>();

  private JPanel myLeftPanel;
  private JPanel myRightPanel;
  private JPanel myCenterPanel;

  private String myInfo;

  private List<String> myCustomComponentIds = new ArrayList<String>();

  private static class WidgetBean {
    JComponent component;
    Position position;
    StatusBarWidget widget;

    static WidgetBean create(@NotNull final StatusBarWidget widget, @NotNull final Position position, @NotNull final JComponent component) {
      final WidgetBean bean = new WidgetBean();
      bean.widget = widget;
      bean.position = position;
      bean.component = component;
      return bean;
    }
  }

  public IdeStatusBarImpl() {
    setLayout(new BorderLayout());
    setBorder(BorderFactory.createEmptyBorder(1, 4, 0, SystemInfo.isMac ? 2 : 0));

    myInfoAndProgressPanel = new InfoAndProgressPanel();
    addWidget(myInfoAndProgressPanel, Position.CENTER);

    setOpaque(true);
    updateUI();
  }

  public void addWidget(@NotNull final StatusBarWidget widget) {
    addWidget(widget, Position.RIGHT, "before Notifications");
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
      public WidgetPresentation getPresentation(@NotNull Type type) {
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
    for (final String key : myWidgetMap.keySet()) {
      final WidgetBean bean = myWidgetMap.get(key);
      if (bean.component instanceof CustomStatusBarWidget && ((CustomStatusBarWidget)bean.component).getComponent() == c) {
        removeWidget(key);
      }
    }
  }

  public void dispose() {
    for (final String key : myWidgetMap.keySet()) {
      final WidgetBean bean = myWidgetMap.get(key);
      Disposer.dispose(bean.widget);
    }
  }

  private void addWidget(@NotNull final StatusBarWidget widget, @NotNull final Position pos, @NotNull String anchor) {
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

    final JComponent c = widget instanceof CustomStatusBarWidget ? ((CustomStatusBarWidget)widget).getComponent() : wrap(widget);
    if (Position.RIGHT == pos && panel.getComponentCount() > 0) {
      final List<String> parts = StringUtil.split(anchor, " ");
      if (parts.size() < 2) {
        throw new IllegalArgumentException(
          "anchor should be a relative position ('before' or 'after') and widget ID, like 'after Encoding'");
      }

      for (final String id : myWidgetMap.keySet()) {
        if (id.equalsIgnoreCase(parts.get(1))) {
          final WidgetBean bean = myWidgetMap.get(id);
          int i = 0;
          for (final Component component : myRightPanel.getComponents()) {
            if (component == bean.component) {
              final String _relative = parts.get(0);
              if ("before".equalsIgnoreCase(_relative)) {
                panel.add(c, i);
                updateBorder(i);
              }
              else {
                final int ndx = i + 1;
                panel.add(c, i + 1);
                updateBorder(ndx);
              }

              installWidget(widget, pos, c);
              return;
            }

            i++;
          }
        }
      }

      //throw new IllegalArgumentException("unable to find widget with id: " + parts.get(1));
    }

    if (Position.LEFT == pos && panel.getComponentCount() == 0) {
      c.setBorder(SystemInfo.isMac ? BorderFactory.createEmptyBorder(2, 0, 2, 4) : BorderFactory.createEmptyBorder());
    }

    panel.add(c);
    installWidget(widget, pos, c);
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
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        myInfo = s;
        if (myInfoAndProgressPanel != null) myInfoAndProgressPanel.setText(s);
      }
    });
  }

  public String getInfo() {
    return myInfo;
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

  public void startRefreshIndication(String tooltipText) {
    myInfoAndProgressPanel.setRefreshToolTipText(tooltipText);
    myInfoAndProgressPanel.setRefreshVisible(true);
  }

  public void stopRefreshIndication() {
    myInfoAndProgressPanel.setRefreshVisible(false);
  }

  public BalloonHandler notifyProgressByBalloon(@NotNull MessageType type, @NotNull String htmlBody) {
    return notifyProgressByBalloon(type, htmlBody, null, null);
  }

  public BalloonHandler notifyProgressByBalloon(@NotNull MessageType type,
                                      @NotNull String htmlBody,
                                      @Nullable Icon icon,
                                      @Nullable HyperlinkListener listener) {
    return myInfoAndProgressPanel.notifyByBalloon(type, htmlBody, icon, listener);
  }

  public void fireNotificationPopup(@NotNull JComponent content, Color backgroundColor) {
    new NotificationPopup(this, content, backgroundColor);
  }

  private void installWidget(@NotNull final StatusBarWidget widget, @NotNull final Position pos, @NotNull final JComponent c) {
    myWidgetMap.put(widget.ID(), WidgetBean.create(widget, pos, c));
    widget.install(this);
  }

  private static JComponent wrap(@NotNull final StatusBarWidget widget) {
    final StatusBarWidget.WidgetPresentation presentation =
      widget.getPresentation(SystemInfo.isMac ? StatusBarWidget.Type.MAC : StatusBarWidget.Type.DEFAULT);
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

  public void removeWidget(@NotNull String id) {
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
      } else {
        myCenterPanel.remove(bean.component);
      }

      myWidgetMap.remove(bean.widget.ID());
      Disposer.dispose(bean.widget);
    }
  }

  public void updateWidgets() {
    for (final String s : myWidgetMap.keySet()) {
      updateWidget(s);
    }
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
      }
    });
  }

  private interface StatusBarWrapper {
    void beforeUpdate();
  }

  private static final class MultipleTextValuesPresentationWrapper extends TextPanel implements StatusBarWrapper {
    private static final Icon ARROWS_ICON = IconLoader.getIcon("/ide/statusbar_arrows.png");

    private StatusBarWidget.MultipleTextValuesPresentation myPresentation;

    private MultipleTextValuesPresentationWrapper(@NotNull final StatusBarWidget.MultipleTextValuesPresentation presentation) {
      super(presentation.getMaxValue());
      myPresentation = presentation;

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(final MouseEvent e) {
          final ListPopup popup = myPresentation.getPopupStep();
          final Dimension dimension = popup.getContent().getPreferredSize();
          final Point at = new Point(0, -dimension.height);
          popup.show(new RelativePoint(e.getComponent(), at));
        }
      });

      setOpaque(false);
    }

    @SuppressWarnings({"unchecked"})
    public void beforeUpdate() {
      setText(myPresentation.getSelectedValue());
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
    private StatusBarWidget.TextPresentation myPresentation;
    private Consumer<MouseEvent> myClickConsumer;
    private boolean myMouseOver;

    private TextPresentationWrapper(@NotNull final StatusBarWidget.TextPresentation presentation) {
      super(presentation.getMaxPossibleText());
      myPresentation = presentation;
      myClickConsumer = myPresentation.getClickConsumer();

      setTextAlignment(presentation.getAlignment());

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

    public void beforeUpdate() {
      setText(myPresentation.getText());
    }
  }

  private static final class IconPresentationWrapper extends JComponent implements StatusBarWrapper {
    private StatusBarWidget.IconPresentation myPresentation;
    private Consumer<MouseEvent> myClickConsumer;
    private Icon myIcon;

    private IconPresentationWrapper(@NotNull final StatusBarWidget.IconPresentation presentation) {
      myPresentation = presentation;
      myClickConsumer = myPresentation.getClickConsumer();
      myIcon = myPresentation.getIcon();

      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(final MouseEvent e) {
          if (myClickConsumer != null && !e.isPopupTrigger() && MouseEvent.BUTTON1 == e.getButton()) {
            myClickConsumer.consume(e);
          }
        }
      });

      ToolTipManager.sharedInstance().registerComponent(this);
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

}

