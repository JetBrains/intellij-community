/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.GotItMessage;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.UIBundle;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.Alarm;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.TimedDeadzone;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
class ToolWindowsWidget extends JLabel implements CustomStatusBarWidget, StatusBarWidget, Disposable,
                                                  UISettingsListener, PropertyChangeListener {

  private final Alarm myAlarm;
  private StatusBar myStatusBar;
  private JBPopup popup;
  private boolean wasExited = false;

  ToolWindowsWidget(@NotNull Disposable parent) {
    new BaseButtonBehavior(this, TimedDeadzone.NULL) {
      @Override
      protected void execute(MouseEvent e) {
        performAction();
      }
    }.setActionTrigger(MouseEvent.MOUSE_PRESSED);

    IdeEventQueue.getInstance().addDispatcher(e -> {
      if (e instanceof MouseEvent) {
        MouseEvent mouseEvent = (MouseEvent)e;
        if (mouseEvent.getComponent() == null || !SwingUtilities.isDescendingFrom(mouseEvent.getComponent(), SwingUtilities.getWindowAncestor(
          this))) {
          return false;
        }

        if (e.getID() == MouseEvent.MOUSE_MOVED && isShowing()) {
          Point p = mouseEvent.getLocationOnScreen();
          Point screen = this.getLocationOnScreen();
          if (new Rectangle(screen.x - 4, screen.y - 2, getWidth() + 4, getHeight() + 4).contains(p)) {
            mouseEntered();
            wasExited = false;
          } else {
            if (!wasExited) {
              wasExited = mouseExited(p);
            }
          }
        } else if (e.getID() == MouseEvent.MOUSE_EXITED) {
          //mouse exits WND
          mouseExited(mouseEvent.getLocationOnScreen());
        }
      }
      return false;
    }, parent);

    UISettings.getInstance().addUISettingsListener(this, this);
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", this);
    myAlarm = new Alarm(parent);
  }

  public boolean mouseExited(Point currentLocationOnScreen) {
    myAlarm.cancelAllRequests();
    if (popup != null && popup.isVisible()) {
      final Point screen = popup.getLocationOnScreen();
      final Rectangle popupScreenRect = new Rectangle(screen.x, screen.y, popup.getSize().width, popup.getSize().height);
      if (! popupScreenRect.contains(currentLocationOnScreen)) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(() -> {
          if (popup != null && popup.isVisible()) {
            popup.cancel();
          }
        }, 300);
        return true;
      }
    }
    return false;
  }

  public void mouseEntered() {
    final boolean active = ApplicationManager.getApplication().isActive();
    if (!active) {
      return;
    }
    if (myAlarm.getActiveRequestCount() == 0) {
      myAlarm.addRequest(() -> {
        final IdeFrameImpl frame = UIUtil.getParentOfType(IdeFrameImpl.class, this);
        if (frame == null) return;

        List<ToolWindow> toolWindows = new ArrayList<>();
        final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(frame.getProject());
        for (String id : toolWindowManager.getToolWindowIds()) {
          final ToolWindow tw = toolWindowManager.getToolWindow(id);
          if (tw.isAvailable() && tw.isShowStripeButton()) {
            toolWindows.add(tw);
          }
        }
        Collections.sort(toolWindows, (o1, o2) -> StringUtil.naturalCompare(o1.getStripeTitle(), o2.getStripeTitle()));

        final JBList list = new JBList(toolWindows);
        list.setCellRenderer(new ListCellRenderer() {
          final JBLabel label = new JBLabel();

          @Override
          public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            final ToolWindow toolWindow = (ToolWindow)value;
            label.setText(toolWindow.getStripeTitle());
            label.setIcon(toolWindow.getIcon());
            label.setBorder(IdeBorderFactory.createEmptyBorder(4, 10, 4, 10));
            label.setForeground(UIUtil.getListForeground(isSelected));
            label.setBackground(UIUtil.getListBackground(isSelected));
            final JPanel panel = new JPanel(new BorderLayout());
            panel.add(label, BorderLayout.CENTER);
            panel.setBackground(UIUtil.getListBackground(isSelected));
            return panel;
          }
        });

        final Dimension size = list.getPreferredSize();
        final JComponent c = this;
        final Insets padding = UIUtil.getListViewportPadding();
        final RelativePoint point = new RelativePoint(c, new Point(-4, -padding.top - padding.bottom -4 - size.height + (SystemInfo.isMac ? 2 : 0)));

        if (popup != null && popup.isVisible()) {
          return;
        }

        list.setSelectedIndex(list.getItemsCount() - 1);
        PopupChooserBuilder builder = JBPopupFactory.getInstance().createListPopupBuilder(list);
        popup = builder
          .setAutoselectOnMouseMove(true)
          .setRequestFocus(false)
          .setItemChoosenCallback(() -> {
            if (popup != null) popup.closeOk(null);
            final Object value = list.getSelectedValue();
            if (value instanceof ToolWindow) {
              ((ToolWindow)value).activate(null, true, true);
            }
          })
          .createPopup();

        popup.show(point);
      }, 300);
    }
  }

  @Override
  public void addNotify() {
    super.addNotify();
    final String key = "toolwindow.stripes.buttons.info.shown";
    if (UISettings.getInstance().HIDE_TOOL_STRIPES && !PropertiesComponent.getInstance().isTrueValue(key)) {
      PropertiesComponent.getInstance().setValue(key, String.valueOf(true));
      final Alarm alarm = new Alarm();
      alarm.addRequest(() -> {
        GotItMessage.createMessage(UIBundle.message("tool.window.quick.access.title"), UIBundle.message(
          "tool.window.quick.access.message"))
          .setDisposable(this)
          .show(new RelativePoint(this, new Point(10, 0)), Balloon.Position.above);
        Disposer.dispose(alarm);
      }, 20000);
    }
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
    setToolTipText(null);
    if (isActive()) {
      boolean changes = false;

      if (!isVisible()) {
        setVisible(true);
        changes = true;
      }

      Icon icon = UISettings.getInstance().HIDE_TOOL_STRIPES ? AllIcons.General.TbShown : AllIcons.General.TbHidden;
      if (icon != getIcon()) {
        setIcon(icon);
        changes = true;
      }

      //Set<Integer> vks = ToolWindowManagerImpl.getActivateToolWindowVKs();
      //String text = "Click to show or hide the tool window bars";
      //if (vks.size() == 1) {
      //  Integer stroke = vks.iterator().next();
      //  String keystrokeText = KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(stroke.intValue(), 0));
      //  text += ".\nDouble-press and hold " + keystrokeText + " to show tool window bars when hidden.";
      //}
      //if (!text.equals(getToolTipText())) {
      //  setToolTipText(text);
      //  changes = true;
      //}

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
    return myStatusBar != null && myStatusBar.getFrame() != null && myStatusBar.getFrame().getProject() != null && Registry
      .is("ide.windowSystem.showTooWindowButtonsSwitcher");
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
    popup = null;
  }
}
