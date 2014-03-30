/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.CloseAction;
import com.intellij.ide.actions.ShowContentAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ToolWindowContentUiType;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.content.*;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.ui.content.tabs.TabbedContentAction;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.switcher.SwitchProvider;
import com.intellij.ui.switcher.SwitchTarget;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.ComparableObject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ToolWindowContentUi extends JPanel implements ContentUI, PropertyChangeListener, DataProvider, SwitchProvider {
  public static final String POPUP_PLACE = "ToolwindowPopup";
  // when client property is put in toolwindow component, hides toolwindow label
  public static final String HIDE_ID_LABEL = "HideIdLabel";

  ContentManager myManager;


  final JPanel myContent = new JPanel(new BorderLayout());
  ToolWindowImpl myWindow;

  TabbedContentAction.CloseAllAction myCloseAllAction;
  TabbedContentAction.MyNextTabAction myNextTabAction;
  TabbedContentAction.MyPreviousTabAction myPreviousTabAction;

  ShowContentAction myShowContent;

  ContentLayout myTabsLayout = new TabContentLayout(this);
  ContentLayout myComboLayout = new ComboContentLayout(this);

  private ToolWindowContentUiType myType = ToolWindowContentUiType.TABBED;
  private boolean myShouldNotShowPopup;

  public ToolWindowContentUi(ToolWindowImpl window) {
    myWindow = window;
    myContent.setOpaque(false);
    myContent.setFocusable(false);
    setOpaque(false);

    myShowContent = new ShowContentAction(myWindow, myContent);

    setBorder(new EmptyBorder(0, 0, 0, 2));
  }

  public void setType(@NotNull ToolWindowContentUiType type) {
    if (myType != type) {

      if (myType != null) {
        getCurrentLayout().reset();
      }

      myType = type;

      getCurrentLayout().init();
      rebuild();
    }
  }

  private ContentLayout getCurrentLayout() {
    assert myManager != null;
    return myType == ToolWindowContentUiType.TABBED ? myTabsLayout : myComboLayout;
  }

  public JComponent getComponent() {
    return myContent;
  }

  public boolean isCycleRoot() {
    return true;
  }

  public JComponent getTabComponent() {
    return this;
  }

  public void setManager(@NotNull final ContentManager manager) {
    if (myManager != null) {
      getCurrentLayout().reset();
    }

    myManager = manager;

    getCurrentLayout().init();

    myManager.addContentManagerListener(new ContentManagerListener() {
      public void contentAdded(final ContentManagerEvent event) {
        getCurrentLayout().contentAdded(event);
        event.getContent().addPropertyChangeListener(ToolWindowContentUi.this);
        rebuild();
      }

      public void contentRemoved(final ContentManagerEvent event) {
        event.getContent().removePropertyChangeListener(ToolWindowContentUi.this);
        getCurrentLayout().contentRemoved(event);
        ensureSelectedContentVisible();
        rebuild();
      }

      public void contentRemoveQuery(final ContentManagerEvent event) {
      }

      public void selectionChanged(final ContentManagerEvent event) {
        ensureSelectedContentVisible();

        update();

        myContent.revalidate();
        myContent.repaint();
      }
    });

    initMouseListeners(this, this);

    rebuild();

    myCloseAllAction = new TabbedContentAction.CloseAllAction(myManager);
    myNextTabAction = new TabbedContentAction.MyNextTabAction(myManager);
    myPreviousTabAction = new TabbedContentAction.MyPreviousTabAction(myManager);
  }

  private void ensureSelectedContentVisible() {
    final Content selected = myManager.getSelectedContent();
    if (selected == null) {
      myContent.removeAll();
      return;
    }

    if (myContent.getComponentCount() == 1) {
      final Component visible = myContent.getComponent(0);
      if (visible == selected.getComponent()) return;
    }

    myContent.removeAll();
    myContent.add(selected.getComponent(), BorderLayout.CENTER);

    myContent.revalidate();
    myContent.repaint();
  }


  private void rebuild() {
    getCurrentLayout().rebuild();
    getCurrentLayout().update();

    revalidate();
    repaint();

    if (myManager.getContentCount() == 0 && myWindow.isToHideOnEmptyContent()) {
      myWindow.hide(null);
    }
  }



  public void doLayout() {
    getCurrentLayout().layout();
  }


  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);
    getCurrentLayout().paintComponent(g);
  }

  protected void paintChildren(final Graphics g) {
    super.paintChildren(g);
    getCurrentLayout().paintChildren(g);
  }

  public Dimension getMinimumSize() {
    Insets insets = getInsets();
    return new Dimension(insets.left + insets.right + getCurrentLayout().getMinimumWidth(), super.getMinimumSize().height);
  }

  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    size.height = 0;
    for (int i = 0; i < getComponentCount(); i++) {
      final Component each = getComponent(i);
      size.height = Math.max(each.getPreferredSize().height, size.height);
    }
    return size;
  }

  public void propertyChange(final PropertyChangeEvent evt) {
    update();
  }

  private void update() {
    getCurrentLayout().update();

    revalidate();
    repaint();
  }

  public boolean isSingleSelection() {
    return true;
  }

  public boolean isToSelectAddedContent() {
    return false;
  }

  public boolean canBeEmptySelection() {
    return false;
  }

  public void beforeDispose() {
  }

  public boolean canChangeSelectionTo(@NotNull Content content, boolean implicit) {
    return true;
  }

  @NotNull
  @Override
  public String getCloseActionName() {
    return getCurrentLayout().getCloseActionName();
  }

  @NotNull
  @Override
  public String getCloseAllButThisActionName() {
    return getCurrentLayout().getCloseAllButThisActionName();
  }

  @NotNull
  @Override
  public String getPreviousContentActionName() {
    return getCurrentLayout().getPreviousContentActionName();
  }

  @NotNull
  @Override
  public String getNextContentActionName() {
    return getCurrentLayout().getNextContentActionName();
  }

  static void initMouseListeners(final JComponent c, final ToolWindowContentUi ui) {
    if (c.getClientProperty(ui) != null) return;


    final Point[] myLastPoint = new Point[1];

    c.addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseDragged(final MouseEvent e) {
        if (myLastPoint[0] == null) return;

        final Window window = SwingUtilities.windowForComponent(c);

        if (window instanceof IdeFrame) return;

        final Rectangle oldBounds = window.getBounds();
        final Point newPoint = e.getPoint();
        SwingUtilities.convertPointToScreen(newPoint, c);
        final Point offset = new Point(newPoint.x - myLastPoint[0].x, newPoint.y - myLastPoint[0].y);
        window.setLocation(oldBounds.x + offset.x, oldBounds.y + offset.y);
        myLastPoint[0] = newPoint;
      }
    });

    c.addMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        myLastPoint[0] = e.getPoint();
        SwingUtilities.convertPointToScreen(myLastPoint[0], c);
        if (!e.isPopupTrigger()) {
          if (!UIUtil.isCloseClick(e)) {
            ui.myWindow.fireActivated();
          }
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (!e.isPopupTrigger()) {
          if (UIUtil.isCloseClick(e, MouseEvent.MOUSE_RELEASED)) {
            ui.processHide(e);
          }
        }
      }
    });


    c.addMouseListener(new PopupHandler() {
      public void invokePopup(final Component comp, final int x, final int y) {
        final Content content = c instanceof BaseLabel ? ((BaseLabel)c).getContent() : null;
        ui.showContextMenu(comp, x, y, ui.myWindow.getPopupGroup(), content);
      }
    });

    c.putClientProperty(ui, Boolean.TRUE);
  }

  private void initActionGroup(DefaultActionGroup group, final Content content) {
    if (content == null) {
      return;
    }
    group.addSeparator();
    group.add(new TabbedContentAction.CloseAction(content));
    group.add(myCloseAllAction);
    group.add(new TabbedContentAction.CloseAllButThisAction(content));
    group.addSeparator();
    if (content.isPinnable()) {
      group.add(PinToolwindowTabAction.getPinAction());
      group.addSeparator();
    }

    group.add(myNextTabAction);
    group.add(myPreviousTabAction);
    group.add(myShowContent);
    group.addSeparator();
  }

  public void showContextMenu(Component comp, int x, int y, ActionGroup toolWindowGroup, @Nullable Content selectedContent) {
    if (selectedContent == null && toolWindowGroup == null) {
      return;
    }
    DefaultActionGroup group = new DefaultActionGroup();
    if (selectedContent != null) {
      initActionGroup(group, selectedContent);
    }

    if (toolWindowGroup != null) {
      group.addAll(toolWindowGroup);
    }

    final ActionPopupMenu popupMenu =
      ((ActionManagerImpl)ActionManager.getInstance()).createActionPopupMenu(POPUP_PLACE, group, new MenuItemPresentationFactory(true));
    popupMenu.getComponent().show(comp, x, y);
  }

  private void processHide(final MouseEvent e) {
    IdeEventQueue.getInstance().blockNextEvents(e);
    final Component c = e.getComponent();
    if (c instanceof BaseLabel) {
      final BaseLabel tab = (BaseLabel)c;
      if (tab.getContent() != null) {
        if (myManager.canCloseContents() && tab.getContent().isCloseable()) {
          myManager.removeContent(tab.getContent(), true, true, true);
        } else {
          if (myManager.getContentCount() == 1) {
            hideWindow(e);
          }
        }
      } else {
        hideWindow(e);
      }
    }
    else {
      hideWindow(e);
    }
  }

  private void hideWindow(final MouseEvent e) {
    if (e.isControlDown()) {
      myWindow.fireHiddenSide();
    }
    else {
      myWindow.fireHidden();
    }
  }

  @Nullable
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.TOOL_WINDOW.is(dataId)) return myWindow;

    if (CloseAction.CloseTarget.KEY.is(dataId)) {
      return computeCloseTarget();
    }

    if (SwitchProvider.KEY.is(dataId) && myType == ToolWindowContentUiType.TABBED) {
      return this;
    }

    return null;
  }


  private CloseAction.CloseTarget computeCloseTarget() {
    if (myManager.canCloseContents()) {
      Content selected = myManager.getSelectedContent();
      if (selected != null && selected.isCloseable()) {
        return new CloseContentTarget(selected);
      }
    }

    return new HideToolwindowTarget();
  }

  private class HideToolwindowTarget implements CloseAction.CloseTarget {
    public void close() {
      myWindow.fireHidden();
    }
  }

  private class CloseContentTarget implements CloseAction.CloseTarget {

    private Content myContent;

    private CloseContentTarget(Content content) {
      myContent = content;
    }

    public void close() {
      myManager.removeContent(myContent, true, true, true);
    }
  }

  public void dispose() {

  }

  boolean isCurrent(ContentLayout layout) {
    return getCurrentLayout() == layout;
  }

  public void toggleContentPopup() {
    if (myShouldNotShowPopup) {
      myShouldNotShowPopup = false;
      return;
    }
    BaseListPopupStep step = new BaseListPopupStep<Content>(null, myManager.getContents()) {
      @Override
      public PopupStep onChosen(Content selectedValue, boolean finalChoice) {
        myManager.setSelectedContent(selectedValue, true, true);
        return FINAL_CHOICE;
      }

      @NotNull
      @Override
      public String getTextFor(Content value) {
        final String displayName = value.getTabName();
        return displayName != null ? displayName : "";
      }

      @Override
      public Icon getIconFor(Content aValue) {
        return aValue.getPopupIcon();
      }

      @Override
      public boolean isMnemonicsNavigationEnabled() {
        return true;
      }

      @Override
      public ListSeparator getSeparatorAbove(Content value) {
        final String separator = value.getSeparator();
        return separator != null ? new ListSeparator(separator) : super.getSeparatorAbove(value);
      }
    };

    step.setDefaultOptionIndex(Arrays.asList(myManager.getContents()).indexOf(myManager.getSelectedContent()));
    final ListPopup popup = new ListPopupImpl(step) {
      @Override
      public void cancel(InputEvent e) {
        super.cancel(e);
        if (e instanceof MouseEvent) {
          final MouseEvent me = (MouseEvent)e;
          final Component component = SwingUtilities.getDeepestComponentAt(e.getComponent(), me.getX(), me.getY());
          if (UIUtil.isActionClick(me) && component instanceof ContentComboLabel &&
              SwingUtilities.isDescendingFrom(component, ToolWindowContentUi.this)) {
            myShouldNotShowPopup = true;
          }
        }
      }
    };
    getCurrentLayout().showContentPopup(popup);
  }

  public List<SwitchTarget> getTargets(boolean onlyVisible, boolean originalProvider) {
    List<SwitchTarget> result = new ArrayList<SwitchTarget>();

    if (myType == ToolWindowContentUiType.TABBED) {
      for (int i = 0; i < myManager.getContentCount(); i++) {
        result.add(new ContentSwitchTarget(myManager.getContent(i)));
      }
    }

    return result;
  }

  public SwitchTarget getCurrentTarget() {
    return new ContentSwitchTarget(myManager.getSelectedContent());
  }

  private class ContentSwitchTarget extends ComparableObject.Impl implements SwitchTarget {

    private Content myContent;

    private ContentSwitchTarget(Content content) {
      myContent = content;
    }

    public ActionCallback switchTo(boolean requestFocus) {
      return myManager.setSelectedContentCB(myContent, requestFocus);
    }

    public boolean isVisible() {
      return true;
    }

    public RelativeRectangle getRectangle() {
      return myTabsLayout.getRectangleFor(myContent);
    }

    public Component getComponent() {
      return myManager.getComponent();
    }

    @Override
    public String toString() {
      return myContent.getDisplayName();
    }

    @NotNull
    @Override
    public Object[] getEqualityObjects() {
      return new Object[] {myContent};
    }
  }
}
