/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ui.content;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.*;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.ui.content.tabs.TabbedContentAction;
import com.intellij.util.IJSwingUtilities;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.TabbedPaneUI;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * @author Eugene Belyaev
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class TabbedPaneContentUI implements ContentUI, PropertyChangeListener {
  public static final String POPUP_PLACE = "TabbedPanePopup";

  private ContentManager myManager;
  private TabbedPaneWrapper myTabbedPaneWrapper;

  /**
   * Creates {@code TabbedPaneContentUI} with bottom tab placement.
   */
  public TabbedPaneContentUI() {
    this(JTabbedPane.BOTTOM);
  }

  /**
   * Creates {@code TabbedPaneContentUI} with cpecified tab placement.
   *
   * @param tabPlacement constant which defines where the tabs are located.
   *                     Acceptable values are {@code javax.swing.JTabbedPane#TOP},
   *                     {@code javax.swing.JTabbedPane#LEFT}, {@code javax.swing.JTabbedPane#BOTTOM}
   *                     and {@code javax.swing.JTabbedPane#RIGHT}.
   */
  public TabbedPaneContentUI(int tabPlacement) {
    myTabbedPaneWrapper = new MyTabbedPaneWrapper(tabPlacement);
  }

  public JComponent getComponent() {
    return myTabbedPaneWrapper.getComponent();
  }

  public void setManager(@NotNull ContentManager manager) {
    if (myManager != null) {
      throw new IllegalStateException();
    }
    myManager = manager;
    myManager.addContentManagerListener(new MyContentManagerListener());
  }

  public void propertyChange(PropertyChangeEvent e) {
    if (Content.PROP_DISPLAY_NAME.equals(e.getPropertyName())) {
      Content content = (Content)e.getSource();
      int index = myTabbedPaneWrapper.indexOfComponent(content.getComponent());
      if (index != -1) {
        myTabbedPaneWrapper.setTitleAt(index, content.getTabName());
      }
    }
    else if (Content.PROP_DESCRIPTION.equals(e.getPropertyName())) {
      Content content = (Content)e.getSource();
      int index = myTabbedPaneWrapper.indexOfComponent(content.getComponent());
      if (index != -1) {
        myTabbedPaneWrapper.setToolTipTextAt(index, content.getDescription());
      }
    }
    else if (Content.PROP_COMPONENT.equals(e.getPropertyName())) {
      Content content = (Content)e.getSource();
      JComponent oldComponent = (JComponent)e.getOldValue();
      int index = myTabbedPaneWrapper.indexOfComponent(oldComponent);
      if (index != -1) {
        boolean hasFocus = IJSwingUtilities.hasFocus2(oldComponent);
        myTabbedPaneWrapper.setComponentAt(index, content.getComponent());
        if (hasFocus) {
          content.getComponent().requestDefaultFocus();
        }
      }
    }
    else if (Content.PROP_ICON.equals(e.getPropertyName())) {
      Content content = (Content)e.getSource();
      int index = myTabbedPaneWrapper.indexOfComponent(content.getComponent());
      if (index != -1) {
        myTabbedPaneWrapper.setIconAt(index, (Icon)e.getNewValue());
      }
    }
  }

  private Content getSelectedContent() {
    JComponent selectedComponent = myTabbedPaneWrapper.getSelectedComponent();
    return myManager.getContent(selectedComponent);
  }




  private class MyTabbedPaneWrapper extends TabbedPaneWrapper.AsJTabbedPane {
    public MyTabbedPaneWrapper(int tabPlacement) {
      super(tabPlacement);
    }

    protected TabbedPane createTabbedPane(int tabPlacement) {
      return new MyTabbedPane(tabPlacement);
    }

    protected TabbedPaneHolder createTabbedPaneHolder() {
      return new MyTabbedPaneHolder(this);
    }

    private class MyTabbedPane extends TabbedPaneImpl {
      public MyTabbedPane(int tabPlacement) {
        super(tabPlacement);
        addMouseListener(new MyPopupHandler());
        enableEvents(AWTEvent.MOUSE_EVENT_MASK);
      }

      private void closeTabAt(int x, int y) {
        TabbedPaneUI ui = getUI();
        int index = ui.tabForCoordinate(this, x, y);
        if (index < 0 || !myManager.canCloseContents()) {
          return;
        }
        final Content content = myManager.getContent(index);
        if (content != null && content.isCloseable()) {
          myManager.removeContent(content, true);
        }
      }

      /**
       * Hides selected menu.
       */
      private void hideMenu() {
        MenuSelectionManager menuSelectionManager = MenuSelectionManager.defaultManager();
        menuSelectionManager.clearSelectedPath();
      }

      protected void processMouseEvent(MouseEvent e) {
        if (e.isPopupTrigger()) { // Popup doesn't activate clicked tab.
          showPopup(e.getX(), e.getY());
          return;
        }

        if (!e.isShiftDown() && (MouseEvent.BUTTON1_MASK & e.getModifiers()) > 0) { // RightClick without Shift modifiers just select tab
          if (MouseEvent.MOUSE_RELEASED == e.getID()) {
            TabbedPaneUI ui = getUI();
            int index = ui.tabForCoordinate(this, e.getX(), e.getY());
            if (index != -1) {
              setSelectedIndex(index);
            }
            hideMenu();
          }
        }
        else if (e.isShiftDown() && (MouseEvent.BUTTON1_MASK & e.getModifiers()) > 0) { // Shift+LeftClick closes the tab
          if (MouseEvent.MOUSE_RELEASED == e.getID()) {
            closeTabAt(e.getX(), e.getY());
            hideMenu();
          }
        }
        else if ((MouseEvent.BUTTON2_MASK & e.getModifiers()) > 0) { // MouseWheelClick closes the tab
          if (MouseEvent.MOUSE_RELEASED == e.getID()) {
            closeTabAt(e.getX(), e.getY());
            hideMenu();
          }
        }
        else if ((MouseEvent.BUTTON3_MASK & e.getModifiers()) > 0 && SystemInfo.isWindows) { // Right mouse button doesn't activate tab
        }
        else {
          super.processMouseEvent(e);
        }
      }

      protected ChangeListener createChangeListener() {
        return new MyModelListener();
      }

      private class MyModelListener extends ModelListener {
        public void stateChanged(ChangeEvent e) {
          Content content = getSelectedContent();
          if (content != null) {
            myManager.setSelectedContent(content);
          }
          super.stateChanged(e);
        }
      }

      /**
       * @return content at the specified location.  {@code x} and {@code y} are in
       *         tabbed pane coordinate system. The method returns {@code null} if there is no contnt at the
       *         specified location.
       */
      private Content getContentAt(int x, int y) {
        TabbedPaneUI ui = getUI();
        int index = ui.tabForCoordinate(this, x, y);
        if (index < 0) {
          return null;
        }
        return myManager.getContent(index);
      }

      protected class MyPopupHandler extends PopupHandler {
        public void invokePopup(Component comp, int x, int y) {
          if (myManager.getContentCount() == 0) return;
          showPopup(x, y);
        }
      }

      /**
       * Shows showPopup menu at the specified location. The {@code x} and {@code y} coordinates
       * are in JTabbedPane coordinate system.
       */
      private void showPopup(int x, int y) {
        Content content = getContentAt(x, y);
        if (content == null) {
          return;
        }
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new TabbedContentAction.CloseAction(content));
        if (myTabbedPaneWrapper.getTabCount() > 1) {
          group.add(new TabbedContentAction.CloseAllAction(myManager));
          group.add(new TabbedContentAction.CloseAllButThisAction(content));
        }
        group.addSeparator();
        group.add(PinToolwindowTabAction.getPinAction());
        group.addSeparator();
        group.add(new TabbedContentAction.MyNextTabAction(myManager));
        group.add(new TabbedContentAction.MyPreviousTabAction(myManager));
        final List<AnAction> additionalActions = myManager.getAdditionalPopupActions(content);
        if (additionalActions != null) {
          group.addSeparator();
          for (AnAction anAction : additionalActions) {
            group.add(anAction);
          }
        }
        ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(POPUP_PLACE, group);
        menu.getComponent().show(myTabbedPaneWrapper.getComponent(), x, y);
      }
    }

    private class MyTabbedPaneHolder extends TabbedPaneHolder implements DataProvider {

      private MyTabbedPaneHolder(TabbedPaneWrapper wrapper) {
        super(wrapper);
      }

      public Object getData(String dataId) {
        if (PlatformDataKeys.CONTENT_MANAGER.is(dataId)) {
          return myManager;
        }
        if (PlatformDataKeys.NONEMPTY_CONTENT_MANAGER.is(dataId) && myManager.getContentCount() > 1) {
          return myManager;
        }
        return null;
      }
    }
  }

  private class MyContentManagerListener extends ContentManagerAdapter {
    public void contentAdded(ContentManagerEvent event) {
      Content content = event.getContent();
      myTabbedPaneWrapper.insertTab(content.getTabName(),
                                    content.getIcon(),
                                    content.getComponent(),
                                    content.getDescription(),
                                    event.getIndex());
      content.addPropertyChangeListener(TabbedPaneContentUI.this);
    }

    public void contentRemoved(ContentManagerEvent event) {
      event.getContent().removePropertyChangeListener(TabbedPaneContentUI.this);
      myTabbedPaneWrapper.removeTabAt(event.getIndex());
    }

    public void selectionChanged(ContentManagerEvent event) {
      int index = event.getIndex();
      if (index != -1 && event.getOperation() != ContentManagerEvent.ContentOperation.remove) {
        myTabbedPaneWrapper.setSelectedIndex(index);
      }
    }
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
    return UIBundle.message("tabbed.pane.close.tab.action.name");
  }

  @NotNull
  @Override
  public String getCloseAllButThisActionName() {
    return UIBundle.message("tabbed.pane.close.all.tabs.but.this.action.name");
  }

  @NotNull
  @Override
  public String getPreviousContentActionName() {
    return "Select Previous Tab";
  }

  @NotNull
  @Override
  public String getNextContentActionName() {
    return "Select Next Tab";
  }

  public void dispose() {
  }
}
