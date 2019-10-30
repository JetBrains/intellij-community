// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.tabs.impl.SingleHeightTabs;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.PanelUI;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.Supplier;

/**
 * @author pegov
 */
public abstract class ToolWindowHeader extends JPanel implements Disposable, UISettingsListener {
  @NotNull private final Supplier<? extends ActionGroup> myGearProducer;

  private ToolWindow myToolWindow;
  private BufferedImage myImage;
  private BufferedImage myActiveImage;
  private ToolWindowType myImageType;

  private final DefaultActionGroup myActionGroup = new DefaultActionGroup();
  private final DefaultActionGroup myActionGroupWest = new DefaultActionGroup();

  private final ActionToolbar myToolbar;
  private ActionToolbar myToolbarWest;
  private final JPanel myWestPanel;

  ToolWindowHeader(final ToolWindowImpl toolWindow, @NotNull final Supplier<? extends ActionGroup> gearProducer) {
    myGearProducer = gearProducer;

    AccessibleContextUtil.setName(this, "Tool Window Header");

    myToolWindow = toolWindow;

    setLayout(new MigLayout("novisualpadding, ins 0, gap 0, fill", "[grow][pref!]"));
    myWestPanel = new NonOpaquePanel(new MigLayout("filly, novisualpadding, ins 0, gap 0"));

    add(myWestPanel, "grow");
    myWestPanel.add(toolWindow.getContentUI().getTabComponent(), "growy");

    ToolWindowContentUi.initMouseListeners(myWestPanel, toolWindow.getContentUI(), true);

    myToolbar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.TOOLWINDOW_TITLE,
      new DefaultActionGroup(myActionGroup, new ShowOptionsAction(), new HideAction()),
      true);
    myToolbar.setTargetComponent(this);
    myToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    myToolbar.setReservePlaceAutoPopupIcon(false);

    JComponent component = myToolbar.getComponent();
    component.setBorder(JBUI.Borders.empty(2, 0));
    component.setOpaque(false);
    add(component);

    myWestPanel.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(final Component comp, final int x, final int y) {
        toolWindow.getContentUI()
          .showContextMenu(comp, x, y, toolWindow.getPopupGroup(), toolWindow.getContentManager().getSelectedContent());
      }
    });
    myWestPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        toolWindow.fireActivated();
      }
    });

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(final MouseEvent e) {
        if (!e.isPopupTrigger()) {
          if (UIUtil.isCloseClick(e, MouseEvent.MOUSE_RELEASED)) {
            if (e.isAltDown()) {
              toolWindow.fireHidden();
            }
            else {
              toolWindow.fireHiddenSide();
            }
          }
          else {
            toolWindow.fireActivated();
          }
        }
      }
    });

    setOpaque(true);
    setBorder(JBUI.Borders.empty(0));

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        ToolWindowManagerImpl mgr = toolWindow.getToolWindowManager();
        mgr.setMaximized(myToolWindow, !mgr.isMaximized(myToolWindow));
        return true;
      }
    }.installOn(myWestPanel);
    myWestPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(final MouseEvent e) {
        Runnable runnable =
          () -> dispatchEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, ToolWindowHeader.this));
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(runnable);
      }
    });
  }

  private void initWestToolBar(JPanel westPanel) {
    myToolbarWest =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLWINDOW_TITLE, new DefaultActionGroup(myActionGroupWest),
                                                      true);

    myToolbarWest.setTargetComponent(this);
    myToolbarWest.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    myToolbarWest.setReservePlaceAutoPopupIcon(false);

    JComponent component = myToolbarWest.getComponent();
    component.setOpaque(false);
    component.setBorder(JBUI.Borders.empty());

    westPanel.add(component);
  }

  @Override
  public void uiSettingsChanged(UISettings uiSettings) {
    clearCaches();
  }

  @Override
  public void dispose() {
    removeAll();
    myToolWindow = null;
  }

  void setTabActions(@NotNull AnAction[] actions) {
    if (myToolbarWest == null) {
      initWestToolBar(myWestPanel);
    }

    myActionGroupWest.removeAll();
    myActionGroupWest.addSeparator();
    myActionGroupWest.addAll(actions);

    if (myToolbarWest != null) {
      myToolbarWest.updateActionsImmediately();
    }
  }

  void setAdditionalTitleActions(@NotNull AnAction[] actions) {
    myActionGroup.removeAll();
    myActionGroup.addAll(actions);
    if (actions.length > 0) {
      myActionGroup.addSeparator();
    }
    myToolbar.updateActionsImmediately();
  }

  @Override
  protected Graphics getComponentGraphics(Graphics g) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(g));
  }

  @Override
  protected void paintComponent(Graphics g) {
    Rectangle r = getBounds();
    Graphics2D g2d = (Graphics2D)g;
    Shape clip = g2d.getClip();

    ToolWindowType type = myToolWindow.getType();

    Image image;
    if (isActive()) {
      if (myActiveImage == null || /*myActiveImage.getHeight() != r.height ||*/ type != myImageType) {
        myActiveImage = drawToBuffer(g2d, true, r.height, type == ToolWindowType.FLOATING);
      }

      image = myActiveImage;
    }
    else {
      if (myImage == null || /*myImage.getHeight() != r.height ||*/ type != myImageType) {
        myImage = drawToBuffer(g2d, false, r.height, type == ToolWindowType.FLOATING);
      }

      image = myImage;
    }

    myImageType = type;

    Rectangle clipBounds = clip.getBounds();
    for (int x = clipBounds.x; x < clipBounds.x + clipBounds.width; x += 150) {
      UIUtil.drawImage(g, image, x, 0, null);
    }
  }

  private static BufferedImage drawToBuffer(Graphics2D g2d, boolean active, int height, boolean floating) {
    final int width = 150;

    BufferedImage image = ImageUtil.createImage(g2d, width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    UIUtil.drawHeader(g, 0, width, height, active, true, !floating, true);
    g.dispose();

    return image;
  }

  @Override
  public void setUI(PanelUI ui) {
    clearCaches();

    super.setUI(ui);
  }

  public void clearCaches() {
    myImage = null;
    myActiveImage = null;
  }

  @Override
  protected void paintChildren(Graphics g) {
    Graphics2D graphics = (Graphics2D)g.create();

    UISettings.setupAntialiasing(graphics);
    super.paintChildren(graphics);

    Rectangle r = getBounds();
    if (!isActive() && !StartupUiUtil.isUnderDarcula()) {
      graphics.setColor(new Color(255, 255, 255, 30));
      graphics.fill(r);
    }

    graphics.dispose();
  }

  protected abstract boolean isActive();

  protected abstract void hideToolWindow();

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    Insets insets = getInsets();
    int height = JBUI.scale(SingleHeightTabs.getUNSCALED_PREF_HEIGHT()) - insets.top - insets.bottom;
    return new Dimension(size.width, height);
  }

  private class ShowOptionsAction extends DumbAwareAction {
    ShowOptionsAction() {
      copyFrom(myGearProducer.get());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final InputEvent inputEvent = e.getInputEvent();
      final ActionPopupMenu popupMenu =
        ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, myGearProducer.get());

      int x = 0;
      int y = 0;
      if (inputEvent instanceof MouseEvent) {
        x = ((MouseEvent)inputEvent).getX();
        y = ((MouseEvent)inputEvent).getY();
      }
      popupMenu.getComponent().show(inputEvent.getComponent(), x, y);
    }
  }

  private class HideAction extends DumbAwareAction {
    HideAction() {
      ActionUtil.copyFrom(this, InternalDecorator.HIDE_ACTIVE_WINDOW_ACTION_ID);
      getTemplatePresentation().setIcon(AllIcons.General.HideToolWindow);
      getTemplatePresentation().setText(UIBundle.message("tool.window.hide.action.name"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      hideToolWindow();
    }

    @Override
    public final void update(@NotNull final AnActionEvent event) {
      event.getPresentation().setEnabled(myToolWindow != null && myToolWindow.isVisible());
    }
  }
}
