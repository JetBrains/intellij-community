// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.Alarm;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TimedDeadzone;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
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
    setBorder(JBUI.Borders.empty());
    new BaseButtonBehavior(this, TimedDeadzone.NULL) {
      @Override
      protected void execute(MouseEvent e) {
        performAction();
      }
    }.setActionTrigger(MouseEvent.MOUSE_PRESSED);

    IdeEventQueue.getInstance().addDispatcher(e -> {
      if (e instanceof MouseEvent) {
        dispatchMouseEvent((MouseEvent)e);
      }
      return false;
    }, parent);

    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(UISettingsListener.TOPIC, this);
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", this);
    myAlarm = new Alarm(parent);
  }

  private void dispatchMouseEvent(MouseEvent e) {
    Component component = e.getComponent();
    if (component != null && SwingUtilities.isDescendingFrom(component, SwingUtilities.getWindowAncestor(this))) {
      int id = e.getID();
      if (id == MouseEvent.MOUSE_MOVED && isShowing()) {
        mouseMoved(e);
      } else if (id == MouseEvent.MOUSE_EXITED) {
        //mouse exits WND
        mouseExited(e.getLocationOnScreen());
      }
    }
  }

  private void mouseMoved(MouseEvent e) {
    Point p = e.getLocationOnScreen();
    Point screen = this.getLocationOnScreen();
    if (new Rectangle(screen.x - 4, screen.y - 2, getWidth() + 4, getHeight() + 4).contains(p)) {
      mouseEntered();
      wasExited = false;
    } else {
      if (!wasExited) {
        wasExited = mouseExited(p);
      }
    }
  }

  private boolean mouseExited(Point currentLocationOnScreen) {
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

  private void mouseEntered() {
    final boolean active = ApplicationManager.getApplication().isActive();
    if (!active) {
      return;
    }
    if (myAlarm.isEmpty()) {
      myAlarm.addRequest(() -> {
        Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this));
        if (project == null) return;

        List<ToolWindow> toolWindows = new ArrayList<>();
        final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        for (String id : toolWindowManager.getToolWindowIds()) {
          final ToolWindow tw = toolWindowManager.getToolWindow(id);
          if (tw.isAvailable() && tw.isShowStripeButton()) {
            toolWindows.add(tw);
          }
        }
        toolWindows.sort((o1, o2) -> StringUtil.naturalCompare(o1.getStripeTitle(), o2.getStripeTitle()));

        JBList<ToolWindow> list = new JBList<>(toolWindows);
        list.setCellRenderer(new ListCellRenderer<ToolWindow>() {
          final JBLabel label = new JBLabel();

          @Override
          public Component getListCellRendererComponent(JList<? extends ToolWindow> list, ToolWindow toolWindow, int index, boolean isSelected, boolean cellHasFocus) {
            label.setText(toolWindow.getStripeTitle());
            label.setIcon(toolWindow.getIcon());
            label.setBorder(JBUI.Borders.empty(4, 10));
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
        final RelativePoint point = new RelativePoint(c, new Point(-4, -padding.top - padding.bottom -4 - size.height + (SystemInfo.isMac
                                                                                                                         ? 2 : 0)));

        if (popup != null && popup.isVisible()) {
          return;
        }

        list.setSelectedIndex(list.getItemsCount() - 1);
        PopupChooserBuilder<ToolWindow> builder = JBPopupFactory.getInstance().createListPopupBuilder(list);
        popup = builder
          .setAutoselectOnMouseMove(true)
          .setRequestFocus(false)
          .setItemChosenCallback((selectedValue) -> {
            if (popup != null) popup.closeOk(null);
            selectedValue.activate(null, true, true);
          })
          .createPopup();

        list.setVisibleRowCount(30); // override default of 15 set when createPopup() is called

        popup.show(point);
      }, 300);
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    updateIcon();
  }

  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    updateIcon();
  }

  private void performAction() {
    if (isActive()) {
      UISettings.getInstance().setHideToolStripes(!UISettings.getInstance().getHideToolStripes());
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

      Icon icon = UISettings.getInstance().getHideToolStripes() ? AllIcons.General.TbShown : AllIcons.General.TbHidden;
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
    return myStatusBar != null && myStatusBar.getProject() != null && Registry.is("ide.windowSystem.showTooWindowButtonsSwitcher");
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
  public WidgetPresentation getPresentation() {
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
