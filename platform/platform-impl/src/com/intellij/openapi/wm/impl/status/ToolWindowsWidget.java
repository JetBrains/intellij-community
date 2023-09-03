// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.ActivateToolWindowAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.toolWindow.ToolWindowEventSource;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.LazyInitializer;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
final class ToolWindowsWidget implements CustomStatusBarWidget, Disposable, UISettingsListener {

  private final LazyInitializer.LazyValue<JLabel> myComponent;
  private final Alarm myAlarm;
  private final StatusBar myStatusBar;
  private JBPopup popup;
  private boolean wasExited = false;

  ToolWindowsWidget(@NotNull Disposable parent, StatusBar statusBar) {
    myStatusBar = statusBar;

  myComponent = LazyInitializer.create(() -> {
    var result = new JLabel();
    BaseButtonBehavior behavior = new BaseButtonBehavior(result, TimedDeadzone.NULL, null) {
      @Override
      protected void execute(MouseEvent e) {
        performAction();
      }
    };
    behavior.setupListeners();
    behavior.setActionTrigger(MouseEvent.MOUSE_PRESSED);
    return result;
  });

    IdeEventQueue.getInstance().addDispatcher(e -> {
      if (e instanceof MouseEvent) {
        dispatchMouseEvent((MouseEvent)e);
      }
      return false;
    }, parent);

    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(UISettingsListener.TOPIC, this);
    FocusUtil.addFocusOwnerListener(this, __ -> {
      updateIcon();
    });
    myAlarm = new Alarm(parent);

    updateIcon();
  }

  private void dispatchMouseEvent(MouseEvent e) {
    var myComponent = this.myComponent.get();
    Component component = e.getComponent();
    if (component != null && SwingUtilities.isDescendingFrom(component, SwingUtilities.getWindowAncestor(myComponent))) {
      int id = e.getID();
      if (id == MouseEvent.MOUSE_MOVED && myComponent.isShowing()) {
        mouseMoved(e);
      }
      else if (id == MouseEvent.MOUSE_EXITED) {
        //mouse exits WND
        mouseExited(e.getLocationOnScreen());
      }
    }
  }

  private void mouseMoved(MouseEvent e) {
    var myComponent = this.myComponent.get();
    Point p = e.getLocationOnScreen();
    Point screen = myComponent.getLocationOnScreen();
    if (new Rectangle(screen.x - 4, screen.y - 2, myComponent.getWidth() + 4, myComponent.getHeight() + 4).contains(p)) {
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
        Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myComponent.get()));
        if (project == null) return;

        UIEventLogger.ToolWindowsWidgetPopupShown.log(project);

        List<ToolWindow> toolWindows = new ArrayList<>();
        ToolWindowManagerImpl toolWindowManager = (ToolWindowManagerImpl)ToolWindowManager.getInstance(project);
        for (ToolWindow toolWindow : toolWindowManager.getToolWindows()) {
          if (toolWindow.isAvailable() && toolWindow.isShowStripeButton()) {
            toolWindows.add(toolWindow);
          }
        }
        toolWindows.sort((o1, o2) -> StringUtil.naturalCompare(o1.getStripeTitle(), o2.getStripeTitle()));

        JBList<ToolWindow> list = new JBList<>(toolWindows);
        list.setCellRenderer(new ToolWindowsWidgetCellRenderer());

        final Dimension size = list.getPreferredSize();
        final JComponent c = myComponent.get();
        final Insets padding = UIUtil.getListViewportPadding(false);
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
            toolWindowManager.activateToolWindow(selectedValue.getId(), null, true, ToolWindowEventSource.ToolWindowsWidget);
          })
          .createPopup();

        list.setVisibleRowCount(30); // override default of 15 set when createPopup() is called

        popup.show(point);
      }, 300);
    }
  }

  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    updateIcon();
  }

  private void performAction() {
    if (isActive()) {
      UIEventLogger.ToolWindowsWidgetPopupClicked.log(myStatusBar.getProject());
      UISettings.getInstance().setHideToolStripes(!UISettings.getInstance().getHideToolStripes());
      UISettings.getInstance().fireUISettingsChanged();
    }
  }

  private void updateIcon() {
    var myComponent = this.myComponent.get();
    myComponent.setToolTipText(null);
    if (isActive()) {
      boolean changes = false;

      if (!myComponent.isVisible()) {
        myComponent.setVisible(true);
        changes = true;
      }

      Icon icon = UISettings.getInstance().getHideToolStripes() ? AllIcons.General.TbShown : AllIcons.General.TbHidden;
      if (icon != myComponent.getIcon()) {
        myComponent.setIcon(icon);
        changes = true;
      }

      if (changes) {
        myComponent.revalidate();
        myComponent.repaint();
      }
    }
    else {
      myComponent.setVisible(false);
      myComponent.setToolTipText(null);
    }
  }

  private boolean isActive() {
    return myStatusBar.getProject() != null;
  }

  @Override
  public JComponent getComponent() {
    return myComponent.get();
  }

  @Override
  public @NotNull String ID() {
    return "ToolWindows Widget";
  }

  @Override
  public WidgetPresentation getPresentation() {
    return null;
  }

  @Override
  public void dispose() {
    Disposer.dispose(this);
    popup = null;
  }

  private static final class ToolWindowsWidgetCellRenderer implements ListCellRenderer<ToolWindow> {
    private final JPanel myPanel;
    private final JLabel myTextLabel = new JLabel();
    private final JLabel myShortcutLabel = new JLabel();

    private ToolWindowsWidgetCellRenderer() {
      myPanel = JBUI.Panels.simplePanel().addToLeft(myTextLabel).addToRight(myShortcutLabel);
      myShortcutLabel.setBorder(JBUI.Borders.empty(0, JBUIScale.scale(8), 1, 0));
      myPanel.setBorder(JBUI.Borders.empty(2, 10));
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends ToolWindow> list,
                                                  ToolWindow value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      UIUtil.setBackgroundRecursively(myPanel, UIUtil.getListBackground(isSelected, true));
      myTextLabel.setText(value.getStripeTitle());
      myTextLabel.setIcon(value.getIcon());
      myTextLabel.setForeground(UIUtil.getListForeground(isSelected, true));
      String activateActionId = ActivateToolWindowAction.getActionIdForToolWindow(value.getId());
      KeyboardShortcut shortcut = ActionManager.getInstance().getKeyboardShortcut(activateActionId);
      if (shortcut != null) {
        myShortcutLabel.setText(KeymapUtil.getShortcutText(shortcut));
      }
      else {
        myShortcutLabel.setText("");
      }
      myShortcutLabel.setForeground(isSelected ? UIManager.getColor("MenuItem.acceleratorSelectionForeground") : UIManager.getColor("MenuItem.acceleratorForeground"));
      return myPanel;
    }
  }
}
