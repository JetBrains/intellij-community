/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.tabs.TabsUtil;
import com.intellij.util.Producer;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.PanelUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author pegov
 */
public abstract class ToolWindowHeader extends JPanel implements Disposable, UISettingsListener {
  @NonNls private static final String HIDE_ACTIVE_WINDOW_ACTION_ID = "HideActiveWindow";
  @NonNls private static final String HIDE_ACTIVE_SIDE_WINDOW_ACTION_ID = "HideSideWindows";

  private ToolWindow myToolWindow;
  private WindowInfoImpl myInfo;
  private final ToolWindowHeader.ActionButton myHideButton;
  private BufferedImage myImage;
  private BufferedImage myActiveImage;
  private ToolWindowType myImageType;
  private final JPanel myButtonPanel;
  private final ToolWindowHeader.ActionButton myGearButton;

  public ToolWindowHeader(final ToolWindowImpl toolWindow, @NotNull WindowInfoImpl info, @NotNull final Producer<ActionGroup> gearProducer) {
    setLayout(new BorderLayout());

    myToolWindow = toolWindow;
    myInfo = info;

    JPanel westPanel = new JPanel() {
      @Override
      public void doLayout() {
        if (getComponentCount() > 0) {
          Rectangle r = getBounds();
          Insets insets = getInsets();

          Component c = getComponent(0);
          Dimension size = c.getPreferredSize();
          if (size.width < (r.width - insets.left - insets.right)) {
            c.setBounds(insets.left, insets.top, r.width, r.height - insets.top - insets.bottom);
          } else {
            c.setBounds(insets.left, insets.top, r.width - insets.left - insets.right, r.height - insets.top - insets.bottom);
          }
        }
      }
    };

    westPanel.setOpaque(false);
    add(westPanel, BorderLayout.CENTER);

    westPanel.add(toolWindow.getContentUI().getTabComponent());

    JPanel eastPanel = new JPanel();
    eastPanel.setOpaque(false);
    eastPanel.setLayout(new BoxLayout(eastPanel, BoxLayout.X_AXIS));
    eastPanel.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));
    add(eastPanel, BorderLayout.EAST);

    myGearButton = new ActionButton(new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final InputEvent inputEvent = e.getInputEvent();
        final ActionPopupMenu popupMenu =
          ((ActionManagerImpl)ActionManager.getInstance())
            .createActionPopupMenu(ToolWindowContentUi.POPUP_PLACE, gearProducer.produce(), new MenuItemPresentationFactory(true));

        int x = 0;
        int y = 0;
        if (inputEvent instanceof MouseEvent) {
          x = ((MouseEvent)inputEvent).getX();
          y = ((MouseEvent)inputEvent).getY();
        }

        popupMenu.getComponent().show(inputEvent.getComponent(), x, y);
      }
    }, AllIcons.General.Gear) {
      @Override
      protected Icon getActiveHoveredIcon() {
        return AllIcons.General.GearHover;
      }
    };

    myHideButton = new ActionButton(new HideAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        hideToolWindow();
      }
    }, new HideSideAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        sideHidden();
      }
    },
                                    AllIcons.General.HideLeft, null, null
    ) {
      @Override
      protected Icon getActiveIcon() {
        return getHideToolWindowIcon(myToolWindow);
      }

      @Override
      protected Icon getAlternativeIcon() {
        return getHideIcon(myToolWindow);
      }

      @Override
      protected Icon getActiveHoveredIcon() {
        return getHideToolWindowHoveredIcon(myToolWindow);
      }

      @Override
      protected Icon getAlternativeHoveredIcon() {
        return getHideHoveredIcon(myToolWindow);
      }
    };

    addDefaultActions(eastPanel);
    myButtonPanel = eastPanel;

    addMouseListener(new PopupHandler() {
      public void invokePopup(final Component comp, final int x, final int y) {
        toolWindow.getContentUI().showContextMenu(comp, x, y, toolWindow.getPopupGroup(), toolWindow.getContentManager().getSelectedContent());
      }
    });

    addMouseListener(new MouseAdapter() {
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

    UISettings.getInstance().addUISettingsListener(this, toolWindow.getContentUI());
  }

  @Override
  public void uiSettingsChanged(UISettings source) {
    clearCaches();
  }

  private void addDefaultActions(JPanel eastPanel) {
    eastPanel.add(myGearButton);
    eastPanel.add(Box.createHorizontalStrut(6));
    eastPanel.add(myHideButton);
    eastPanel.add(Box.createHorizontalStrut(1));
  }

  @Override
  public void dispose() {
    removeAll();
    myToolWindow = null;
    myInfo = null;
  }

  public void updateTooltips() {
    if (myHideButton != null) {
      myHideButton.updateTooltip();
    }
  }

  public void setAdditionalTitleActions(AnAction[] actions) {
    myButtonPanel.removeAll();
    boolean actionAdded = false;
    for (final AnAction action : actions) {
      if (action == null) continue;
      myButtonPanel.add(new ActionButton(action, action.getTemplatePresentation().getIcon()) {
        @Override
        protected Icon getActiveHoveredIcon() {
          final Icon icon = action.getTemplatePresentation().getHoveredIcon();
          return icon != null ? icon : super.getActiveHoveredIcon();
        }
      });
      myButtonPanel.add(Box.createHorizontalStrut(9));
      actionAdded = true;
    }
    if (actionAdded) {
      myButtonPanel.add(new JLabel(AllIcons.General.Divider));
      myButtonPanel.add(Box.createHorizontalStrut(6));
    }
    addDefaultActions(myButtonPanel);
  }

  private static Icon getHideToolWindowIcon(ToolWindow toolWindow) {
    ToolWindowAnchor anchor = toolWindow.getAnchor();
    if (anchor == ToolWindowAnchor.BOTTOM) {
      return AllIcons.General.HideDownPart;
    }
    else if (anchor == ToolWindowAnchor.RIGHT) {
      return AllIcons.General.HideRightPart;
    }

    return AllIcons.General.HideLeftPart;
  }

  private static Icon getHideIcon(ToolWindow toolWindow) {
    ToolWindowAnchor anchor = toolWindow.getAnchor();
    if (anchor == ToolWindowAnchor.BOTTOM) {
      return AllIcons.General.HideDown;
    }
    else if (anchor == ToolWindowAnchor.RIGHT) {
      return AllIcons.General.HideRight;
    }

    return AllIcons.General.HideLeft;
  }

  private static Icon getHideToolWindowHoveredIcon(ToolWindow toolWindow) {
    ToolWindowAnchor anchor = toolWindow.getAnchor();
    if (anchor == ToolWindowAnchor.BOTTOM) {
      return AllIcons.General.HideDownPartHover;
    }
    else if (anchor == ToolWindowAnchor.RIGHT) {
      return AllIcons.General.HideRightPartHover;
    }

    return AllIcons.General.HideLeftPartHover;
  }

  private static Icon getHideHoveredIcon(ToolWindow toolWindow) {
    ToolWindowAnchor anchor = toolWindow.getAnchor();
    if (anchor == ToolWindowAnchor.BOTTOM) {
      return AllIcons.General.HideDownHover;
    }
    else if (anchor == ToolWindowAnchor.RIGHT) {
      return AllIcons.General.HideRightHover;
    }

    return AllIcons.General.HideLeftHover;
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
        myActiveImage = drawToBuffer(true, r.height, myToolWindow.getType() == ToolWindowType.FLOATING);
      }

      image = myActiveImage;
    } else {
      if (myImage == null || /*myImage.getHeight() != r.height ||*/ type != myImageType) {
        myImage = drawToBuffer(false, r.height, myToolWindow.getType() == ToolWindowType.FLOATING);
      }

      image = myImage;
    }

    myImageType = myToolWindow.getType();

    Rectangle clipBounds = clip.getBounds();
    for (int x = clipBounds.x; x < clipBounds.x + clipBounds.width; x+=150) {
      UIUtil.drawImage(g, image, x, 0, null);
    }
  }

  private static BufferedImage drawToBuffer(boolean active, int height, boolean floating) {
    final int width = 150;

    BufferedImage image = UIUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB);
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

    UIUtil.applyRenderingHints(graphics);
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

  protected abstract void toolWindowTypeChanged(ToolWindowType type);

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    return new Dimension(size.width, TabsUtil.getTabsHeight());
  }

  @Override
  public Dimension getMinimumSize() {
    Dimension size = super.getMinimumSize();
    return new Dimension(size.width, TabsUtil.getTabsHeight());
  }

  private class ActionButton extends Wrapper implements ActionListener, AltStateManager.AltListener {
    private final InplaceButton myButton;
    private final AnAction myAction;
    private final AnAction myAlternativeAction;
    private final Icon myActiveIcon;
    private final Icon myInactiveIcon;
    private final Icon myAlternativeIcon;

    private AnAction myCurrentAction;

    public ActionButton(AnAction action, AnAction alternativeAction, @NotNull Icon activeIcon, Icon inactiveIcon,
                        Icon alternativeIcon) {
      myAction = action;
      myAlternativeAction = alternativeAction;

      myActiveIcon = activeIcon;
      myInactiveIcon = inactiveIcon;
      myAlternativeIcon = alternativeIcon;

      myCurrentAction = myAction;

      myButton = new InplaceButton(getToolTipTextByAction(action),
                                   EmptyIcon.ICON_16, this) {
        @Override
        public boolean isActive() {
          return ActionButton.this.isActive();
        }
      };

      myButton.setHoveringEnabled(!SystemInfo.isMac);
      setContent(myButton);
      setOpaque(false);

      setIcon(getActiveIcon(), getInactiveIcon() == null ? getActiveIcon() : getInactiveIcon(), getActiveHoveredIcon());

      PropertyChangeListener listener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          if (myAlternativeAction == null) return;
          if ("ancestor".equals(evt.getPropertyName())) {
            if (evt.getNewValue() == null) {
              AltStateManager.getInstance().removeListener(ActionButton.this);
              switchAlternativeAction(false);
            }
            else {
              AltStateManager.getInstance().addListener(ActionButton.this);
            }
          }
        }
      };

      addPropertyChangeListener(listener);
    }

    public void updateTooltip() {
      myButton.setToolTipText(getToolTipTextByAction(myCurrentAction));
    }

    protected Icon getActiveIcon() {
      return myActiveIcon;
    }

    protected Icon getActiveHoveredIcon() {
      return myActiveIcon;
    }

    protected Icon getInactiveIcon() {
      return myInactiveIcon;
    }

    protected Icon getAlternativeIcon() {
      return myAlternativeIcon;
    }

    protected Icon getAlternativeHoveredIcon() {
      return myAlternativeIcon;
    }

    private void switchAlternativeAction(boolean b) {
      if (b && myCurrentAction == myAlternativeAction) return;
      if (!b && myCurrentAction != myAlternativeAction) return;

      setIcon(b ? getAlternativeIcon() : getActiveIcon(),
              b ? getAlternativeIcon() : getInactiveIcon() == null ? getActiveIcon() : getInactiveIcon(),
              b ? getAlternativeHoveredIcon() : getActiveHoveredIcon());
      myCurrentAction = b ? myAlternativeAction : myAction;

      setToolTipText(getToolTipTextByAction(myCurrentAction));

      repaint();
    }

    public ActionButton(AnAction action, @NotNull Icon activeIcon, Icon inactiveIcon) {
      this(action, null, activeIcon, inactiveIcon, null);
    }

    public ActionButton(AnAction action, Icon activeIcon) {
      this(action, activeIcon, activeIcon);
    }

    public void actionPerformed(final ActionEvent e) {
      AnAction action =
        myAlternativeAction != null && (e.getModifiers() & InputEvent.ALT_MASK) == InputEvent.ALT_MASK ? myAlternativeAction : myAction;
      final DataContext dataContext = DataManager.getInstance().getDataContext(this);
      final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
      InputEvent inputEvent = e.getSource() instanceof InputEvent ? (InputEvent) e.getSource() : null;
      final AnActionEvent event =
        new AnActionEvent(inputEvent, dataContext, ActionPlaces.UNKNOWN, action.getTemplatePresentation(),
                          ActionManager.getInstance(),
                          0);
      actionManager.fireBeforeActionPerformed(action, dataContext, event);
      final Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
      if (component != null && !component.isShowing()) {
        return;
      }

      action.actionPerformed(event);
    }

    public boolean isActive() {
      return ToolWindowHeader.this.isActive();
    }

    public void setIcon(final Icon active, Icon inactive, Icon hovered) {
      myButton.setIcons(active, inactive, hovered);
    }

    public void setToolTipText(final String text) {
      myButton.setToolTipText(text);
    }

    @Override
    public void altPressed() {
      PointerInfo info = MouseInfo.getPointerInfo();
      if (info != null) {
        Point p = info.getLocation();
        SwingUtilities.convertPointFromScreen(p, this);
        switchAlternativeAction(myButton.getBounds().contains(p));
      }
    }

    @Override
    public void altReleased() {
      switchAlternativeAction(false);
    }
  }

  private static String getToolTipTextByAction(AnAction action) {
    String text = KeymapUtil.createTooltipText(action.getTemplatePresentation().getText(), action);

    if (action instanceof HideAction) {
      text += String.format(" (Click with %s to Hide Side)", KeymapUtil.getShortcutText(KeyboardShortcut.fromString("pressed ALT")));
    }

    return text;
  }

  private abstract class HideSideAction extends AnAction implements DumbAware {
    @NonNls public static final String HIDE_ACTIVE_SIDE_WINDOW_ACTION_ID = ToolWindowHeader.HIDE_ACTIVE_SIDE_WINDOW_ACTION_ID;

    public HideSideAction() {
      copyFrom(ActionManager.getInstance().getAction(HIDE_ACTIVE_SIDE_WINDOW_ACTION_ID));
      getTemplatePresentation().setText(UIBundle.message("tool.window.hideSide.action.name"));
    }

    public abstract void actionPerformed(final AnActionEvent e);

    public final void update(final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(myInfo.isVisible());
    }
  }

  private abstract class HideAction extends AnAction implements DumbAware {
    @NonNls public static final String HIDE_ACTIVE_WINDOW_ACTION_ID = ToolWindowHeader.HIDE_ACTIVE_WINDOW_ACTION_ID;

    public HideAction() {
      copyFrom(ActionManager.getInstance().getAction(HIDE_ACTIVE_WINDOW_ACTION_ID));
      getTemplatePresentation().setText(UIBundle.message("tool.window.hide.action.name"));
    }

    public final void update(final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(myInfo.isVisible());
    }
  }
}
