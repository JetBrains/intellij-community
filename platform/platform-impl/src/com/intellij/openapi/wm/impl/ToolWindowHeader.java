/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.IconLoader;
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
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author pegov
 */
public abstract class ToolWindowHeader extends JPanel implements Disposable {
  @NonNls private static final String HIDE_ACTIVE_WINDOW_ACTION_ID = "HideActiveWindow";
  @NonNls private static final String HIDE_ACTIVE_SIDE_WINDOW_ACTION_ID = "HideSideWindows";

  private static final Icon ourHideLeftSideIcon = IconLoader.getIcon("/general/hideLeft.png");
  private static final Icon ourHideRightSideIcon = IconLoader.getIcon("/general/hideRight.png");
  private static final Icon ourHideDownSideIcon = IconLoader.getIcon("/general/hideDown.png");

  private static final Icon ourHideLeftIcon = IconLoader.getIcon("/general/hideLeftPart.png");
  private static final Icon ourHideRightIcon = IconLoader.getIcon("/general/hideRightPart.png");
  private static final Icon ourHideDownIcon = IconLoader.getIcon("/general/hideDownPart.png");

  private static final Icon ourSettingsIcon = IconLoader.getIcon("/general/gear.png");

  private ToolWindow myToolWindow;
  private WindowInfoImpl myInfo;
  private final ToolWindowHeader.ActionButton myHideButton;

  public ToolWindowHeader(final ToolWindowImpl toolWindow, WindowInfoImpl info, @NotNull final Producer<ActionGroup> gearProducer) {
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
            c.setBounds(insets.left, insets.top, size.width, r.height - insets.top - insets.bottom);
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

    eastPanel.add(new ActionButton(new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final InputEvent inputEvent = e.getInputEvent();
        final ActionPopupMenu popupMenu =
          ActionManager.getInstance().createActionPopupMenu(ToolWindowContentUi.POPUP_PLACE, gearProducer.produce());

        int x = 0;
        int y = 0;
        if (inputEvent instanceof MouseEvent) {
          x = ((MouseEvent)inputEvent).getX();
          y = ((MouseEvent)inputEvent).getY();
        }

        popupMenu.getComponent().show(inputEvent.getComponent(), x, y);
      }
    }, ourSettingsIcon));

    eastPanel.add(Box.createHorizontalStrut(3));

    myHideButton = new ActionButton(new HideSideAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        sideHidden();
      }
    }, new HideAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        hideToolWindow();
      }
    },
                                    ourHideLeftSideIcon, null, null
    ) {
      @Override
      protected Icon getActiveIcon() {
        return getHideIcon(myToolWindow);
      }

      @Override
      protected Icon getAlternativeIcon() {
        return getHideToolWindowIcon(myToolWindow);
      }
    };

    eastPanel.add(myHideButton);

    addMouseListener(new PopupHandler() {
      public void invokePopup(final Component comp, final int x, final int y) {
        toolWindow.getContentUI().showContextMenu(comp, x, y, toolWindow.getPopupGroup());
      }
    });
    
    addMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        if (!e.isPopupTrigger()) {
          if (UIUtil.isCloseClick(e)) {
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
  
  private static Icon getHideToolWindowIcon(ToolWindow toolWindow) {
    ToolWindowAnchor anchor = toolWindow.getAnchor();
    if (anchor == ToolWindowAnchor.BOTTOM) {
      return ourHideDownIcon;
    }
    else if (anchor == ToolWindowAnchor.RIGHT) {
      return ourHideRightIcon;
    }

    return ourHideLeftIcon;
  }

  private static Icon getHideIcon(ToolWindow toolWindow) {
    ToolWindowAnchor anchor = toolWindow.getAnchor();
    if (anchor == ToolWindowAnchor.BOTTOM) {
      return ourHideDownSideIcon;
    }
    else if (anchor == ToolWindowAnchor.RIGHT) {
      return ourHideRightSideIcon;
    }

    return ourHideLeftSideIcon;
  }

  @Override
  protected void paintComponent(Graphics g) {
    Rectangle r = getBounds();

    Graphics2D g2d = (Graphics2D)g;

    Shape clip = g2d.getClip();

    g2d.setColor(UIUtil.getPanelBackground());
    g2d.fill(clip);

    g2d.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 5), 0, r.height, new Color(0, 0, 0, 20)));
    g2d.fill(clip);

    g2d.setColor(new Color(0, 0, 0, 90));
    g2d.drawLine(r.x, r.y, r.width - 1, r.y);
    g2d.drawLine(r.x, r.height - 1, r.width - 1, r.height - 1);

    g2d.setColor(new Color(255, 255, 255, 100));
    g2d.drawLine(r.x, r.y + 1, r.width - 1, r.y + 1);

    if (isActive()) {
      g2d.setColor(new Color(100, 150, 230, 50));
      g2d.fill(clip);
    }
  }

  @Override
  protected void paintChildren(Graphics g) {
    super.paintChildren(g);

    Rectangle r = getBounds();
    Graphics2D g2d = (Graphics2D)g;
    if (!isActive()) {
      g2d.setColor(new Color(255, 255, 255, 30));
      g2d.fill(r);
    }
  }

  protected abstract boolean isActive();

  protected abstract void hideToolWindow();

  protected abstract void sideHidden();

  protected abstract void toolWindowTypeChanged(ToolWindowType type);

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    return new Dimension(size.width, TabsUtil.getTabsHeight() + TabsUtil.TABS_BORDER * 2);
  }

  @Override
  public Dimension getMinimumSize() {
    Dimension size = super.getMinimumSize();
    return new Dimension(size.width, TabsUtil.getTabsHeight() + TabsUtil.TABS_BORDER * 2);
  }

  private class ActionButton extends Wrapper implements ActionListener, AltStateManager.AltListener {
    private final InplaceButton myButton;
    private final AnAction myAction;
    private AnAction myAlternativeAction;
    private Icon myActiveIcon;
    private Icon myInactiveIcon;
    private Icon myAlternativeIcon;

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

      myButton.setHoveringEnabled(false);
      setContent(myButton);
      setOpaque(false);

      setIcon(getActiveIcon(), getInactiveIcon() == null ? getActiveIcon() : getInactiveIcon());

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

    protected Icon getInactiveIcon() {
      return myInactiveIcon;
    }

    protected Icon getAlternativeIcon() {
      return myAlternativeIcon;
    }

    private void switchAlternativeAction(boolean b) {
      if (b && myCurrentAction == myAlternativeAction) return;
      if (!b && myCurrentAction != myAlternativeAction) return;

      setIcon(b ? getAlternativeIcon() : getActiveIcon(),
              b ? getAlternativeIcon() : getInactiveIcon() == null ? getActiveIcon() : getInactiveIcon());
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

    public void setIcon(final Icon active, Icon inactive) {
      myButton.setIcons(active, inactive, active);
    }

    public void setToolTipText(final String text) {
      myButton.setToolTipText(text);
    }

    @Override
    public void altPressed() {
      PointerInfo info = MouseInfo.getPointerInfo();
      Point p = info.getLocation();
      SwingUtilities.convertPointFromScreen(p, this);
      switchAlternativeAction(myButton.getBounds().contains(p));
    }

    @Override
    public void altReleased() {
      switchAlternativeAction(false);
    }
  }

  private static String getToolTipTextByAction(AnAction action) {
    String text = action.getTemplatePresentation().getText();
    final String shortcutForAction = KeymapUtil.getFirstKeyboardShortcutText(action);
    if (shortcutForAction.length() > 0) {
      text += "  " + shortcutForAction;
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
