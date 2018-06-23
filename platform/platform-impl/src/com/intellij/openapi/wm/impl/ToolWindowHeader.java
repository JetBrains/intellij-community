// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.internal.statistic.collectors.fus.ui.persistence.ToolbarClicksCollector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.tabs.TabsUtil;
import com.intellij.util.Producer;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.PanelUI;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * @author pegov
 */
public abstract class ToolWindowHeader extends JPanel implements Disposable, UISettingsListener {
  @NotNull private final Producer<ActionGroup> myGearProducer;

  private ToolWindow myToolWindow;
  private BufferedImage myImage;
  private BufferedImage myActiveImage;
  private ToolWindowType myImageType;

  private final DefaultActionGroup myActionGroup = new DefaultActionGroup();
  private ActionToolbar myToolbar;

  /**
   * @deprecated
   * @param info won't be used anymore
   */
  @Deprecated
  ToolWindowHeader(final ToolWindowImpl toolWindow, @NotNull WindowInfoImpl info, @NotNull final Producer<ActionGroup> gearProducer) {
    this(toolWindow, gearProducer);
  }

  ToolWindowHeader(final ToolWindowImpl toolWindow, @NotNull final Producer<ActionGroup> gearProducer) {
    myGearProducer = gearProducer;
    setLayout(new BorderLayout());
    AccessibleContextUtil.setName(this, "Tool Window Header");

    myToolWindow = toolWindow;

    JPanel westPanel = new NonOpaquePanel() {
      @Override
      public void doLayout() {
        if (getComponentCount() > 0) {
          Rectangle r = getBounds();
          Insets insets = getInsets();

          Component c = getComponent(0);
          Dimension size = c.getPreferredSize();
          if (size.width < r.width - insets.left - insets.right) {
            c.setBounds(insets.left, insets.top, size.width, r.height - insets.top - insets.bottom);
          } else {
            c.setBounds(insets.left, insets.top, r.width - insets.left - insets.right, r.height - insets.top - insets.bottom);
          }
        }
      }
    };

    add(westPanel, BorderLayout.CENTER);

    westPanel.add(toolWindow.getContentUI().getTabComponent());
    ToolWindowContentUi.initMouseListeners(westPanel, toolWindow.getContentUI(), true);

    myToolbar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.TOOLWINDOW_TITLE,
      new DefaultActionGroup(myActionGroup, new ShowOptionsAction(), new HideAction()),
      true);
    myToolbar.setTargetComponent(this);
    myToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    myToolbar.setReservePlaceAutoPopupIcon(false);
    
    JComponent component = myToolbar.getComponent();
    component.setBorder(JBUI.Borders.empty());
    component.setOpaque(false);
    add(component, BorderLayout.EAST);

    westPanel.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(final Component comp, final int x, final int y) {
        toolWindow.getContentUI().showContextMenu(comp, x, y, toolWindow.getPopupGroup(), toolWindow.getContentManager().getSelectedContent());
      }
    });
    westPanel.addMouseListener(new MouseAdapter() {
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
    setBorder(BorderFactory.createEmptyBorder(TabsUtil.TABS_BORDER, 1, TabsUtil.TABS_BORDER, 1));

    new DoubleClickListener(){
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        ToolWindowManagerImpl mgr = toolWindow.getToolWindowManager();
        mgr.setMaximized(myToolWindow, !mgr.isMaximized(myToolWindow));
        return true;
      }
    }.installOn(westPanel);
    westPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(final MouseEvent e) {
        Runnable runnable =
          () -> dispatchEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, ToolWindowHeader.this));
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(runnable);
      }
    });
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

  void setAdditionalTitleActions(AnAction[] actions) {
    myActionGroup.removeAll();
    myActionGroup.addAll(actions);
    if (actions.length > 0) {
      myActionGroup.addSeparator();
    }
    if (myToolbar != null) {
      myToolbar.updateActionsImmediately();
    }
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
        myActiveImage = drawToBuffer(g2d, true, r.height, myToolWindow.getType() == ToolWindowType.FLOATING);
      }

      image = myActiveImage;
    } else {
      if (myImage == null || /*myImage.getHeight() != r.height ||*/ type != myImageType) {
        myImage = drawToBuffer(g2d, false, r.height, myToolWindow.getType() == ToolWindowType.FLOATING);
      }

      image = myImage;
    }

    myImageType = myToolWindow.getType();

    Rectangle clipBounds = clip.getBounds();
    for (int x = clipBounds.x; x < clipBounds.x + clipBounds.width; x+=150) {
      UIUtil.drawImage(g, image, x, 0, null);
    }
  }

  private static BufferedImage drawToBuffer(Graphics2D g2d, boolean active, int height, boolean floating) {
    final int width = 150;

    BufferedImage image = UIUtil.createImage(g2d, width, height, BufferedImage.TYPE_INT_RGB);
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
    Graphics2D graphics = (Graphics2D) g.create();

    UISettings.setupAntialiasing(graphics);
    super.paintChildren(graphics);

    Rectangle r = getBounds();
    if (!isActive() && !UIUtil.isUnderDarcula()) {
      graphics.setColor(new Color(255, 255, 255, 30));
      graphics.fill(r);
    }

    graphics.dispose();
  }

  protected abstract boolean isActive();

  protected abstract void hideToolWindow();

  protected abstract void sideHidden();

  protected abstract void toolWindowTypeChanged(@NotNull ToolWindowType type);

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    return new Dimension(size.width, TabsUtil.getTabsHeight(JBUI.CurrentTheme.ToolWindow.tabVerticalPadding()));
  }

  @Override
  public Dimension getMinimumSize() {
    Dimension size = super.getMinimumSize();
    return new Dimension(size.width, TabsUtil.getTabsHeight(JBUI.CurrentTheme.ToolWindow.tabVerticalPadding()));
  }

  private class ShowOptionsAction extends DumbAwareAction {
    ShowOptionsAction() {
      copyFrom(myGearProducer.produce());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final InputEvent inputEvent = e.getInputEvent();
      final ActionPopupMenu popupMenu =
        ((ActionManagerImpl)ActionManager.getInstance())
          .createActionPopupMenu(ToolWindowContentUi.POPUP_PLACE, myGearProducer.produce(), new MenuItemPresentationFactory(true));

      int x = 0;
      int y = 0;
      if (inputEvent instanceof MouseEvent) {
        x = ((MouseEvent)inputEvent).getX();
        y = ((MouseEvent)inputEvent).getY();
      }
      ToolbarClicksCollector.record("Show Options", "ToolWindowHeader");
      popupMenu.getComponent().show(inputEvent.getComponent(), x, y);
    }
  }
  
  private class HideAction extends DumbAwareAction {
    public HideAction() {
      copyFrom(ActionManager.getInstance().getAction(InternalDecorator.HIDE_ACTIVE_WINDOW_ACTION_ID));
      getTemplatePresentation().setIcon(AllIcons.General.HideToolWindow);
      getTemplatePresentation().setText(UIBundle.message("tool.window.hide.action.name"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      hideToolWindow();
    }

    @Override
    public final void update(@NotNull final AnActionEvent event) {
      event.getPresentation().setEnabled(myToolWindow.isVisible());
    }
  }
}
