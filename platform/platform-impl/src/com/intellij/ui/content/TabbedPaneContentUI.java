// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.ui.content.tabs.TabbedContentAction;
import com.intellij.util.IJSwingUtilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.TabbedPaneUI;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
public final class TabbedPaneContentUI implements ContentUI, PropertyChangeListener {
  public static final @NonNls String POPUP_PLACE = "TabbedPanePopup";

  private ContentManager myManager;
  private final TabbedPaneWrapper myTabbedPaneWrapper;

  /**
   * Creates {@code TabbedPaneContentUI} with bottom tab placement.
   */
  public TabbedPaneContentUI() {
    this(SwingConstants.BOTTOM);
  }

  /**
   * Creates {@code TabbedPaneContentUI} with specified tab placement.
   *
   * @param tabPlacement constant which defines where the tabs are located.
   *                     Acceptable values are {@code javax.swing.JTabbedPane#TOP},
   *                     {@code javax.swing.JTabbedPane#LEFT}, {@code javax.swing.JTabbedPane#BOTTOM}
   *                     and {@code javax.swing.JTabbedPane#RIGHT}.
   */
  public TabbedPaneContentUI(int tabPlacement) {
    myTabbedPaneWrapper = new MyTabbedPaneWrapper(tabPlacement);
  }

  @Override
  public JComponent getComponent() {
    return myTabbedPaneWrapper.getComponent();
  }

  @Override
  public void setManager(@NotNull ContentManager manager) {
    if (myManager != null) {
      throw new IllegalStateException();
    }
    myManager = manager;
    myManager.addContentManagerListener(new MyContentManagerListener());
  }

  @ApiStatus.Internal
  public @Nullable ContentManager getManager() {
    return myManager;
  }

  @Override
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
    return selectedComponent == null ? null : myManager.getContent(selectedComponent);
  }

  public final class MyTabbedPaneWrapper extends TabbedPaneWrapper.AsJTabbedPane {
    MyTabbedPaneWrapper(int tabPlacement) {
      super(tabPlacement);
    }

    @Override
    protected TabbedPane createTabbedPane(int tabPlacement) {
      return new MyTabbedPane(tabPlacement);
    }

    @Override
    protected TabbedPaneHolder createTabbedPaneHolder() {
      return new MyTabbedPaneHolder(this);
    }

    public ContentManager getContentManager() {
      return myManager;
    }

    private final class MyTabbedPane extends TabbedPaneImpl {
      MyTabbedPane(int tabPlacement) {
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
      private static void hideMenu() {
        MenuSelectionManager menuSelectionManager = MenuSelectionManager.defaultManager();
        menuSelectionManager.clearSelectedPath();
      }

      @Override
      protected void processMouseEvent(MouseEvent e) {
        if (e.isPopupTrigger()) { // Popup doesn't activate clicked tab.
          showPopup(e.getX(), e.getY());
          return;
        }

        if (!e.isShiftDown() && (InputEvent.BUTTON1_MASK & e.getModifiers()) > 0) { // RightClick without Shift modifiers just select tab
          if (MouseEvent.MOUSE_RELEASED == e.getID()) {
            TabbedPaneUI ui = getUI();
            int index = ui.tabForCoordinate(this, e.getX(), e.getY());
            if (index != -1) {
              setSelectedIndex(index);
              // Always request a focus for tab component when user clicks on tab header.
              IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(
                () -> IdeFocusManager.getGlobalInstance().requestFocus(MyTabbedPaneWrapper.this.getComponent(), true));
            }
            hideMenu();
          }
        }
        else if (e.isShiftDown() && (InputEvent.BUTTON1_MASK & e.getModifiers()) > 0) { // Shift+LeftClick closes the tab
          if (MouseEvent.MOUSE_RELEASED == e.getID()) {
            closeTabAt(e.getX(), e.getY());
            hideMenu();
          }
        }
        else if ((InputEvent.BUTTON2_MASK & e.getModifiers()) > 0) { // MouseWheelClick closes the tab
          if (MouseEvent.MOUSE_RELEASED == e.getID()) {
            closeTabAt(e.getX(), e.getY());
            hideMenu();
          }
        }
        else if ((InputEvent.BUTTON3_MASK & e.getModifiers()) > 0 && SystemInfo.isWindows) { // Right mouse button doesn't activate tab
        }
        else {
          super.processMouseEvent(e);
        }
      }

      @Override
      protected ChangeListener createChangeListener() {
        return new MyModelListener();
      }

      private final class MyModelListener extends ModelListener {
        @Override
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
       *         tabbed pane coordinate system. The method returns {@code null} if there is no content at the
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

      protected final class MyPopupHandler extends PopupHandler {
        @Override
        public void invokePopup(Component comp, int x, int y) {
          if (myManager.isEmpty()) return;
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
        if (!additionalActions.isEmpty()) {
          group.addSeparator();
          group.addAll(additionalActions);
        }
        ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(POPUP_PLACE, group);
        menu.getComponent().show(myTabbedPaneWrapper.getComponent(), x, y);
      }
    }

    private final class MyTabbedPaneHolder extends TabbedPaneHolder implements UiDataProvider {

      private MyTabbedPaneHolder(TabbedPaneWrapper wrapper) {
        super(wrapper);
      }

      @Override
      public void uiDataSnapshot(@NotNull DataSink sink) {
        sink.set(PlatformDataKeys.CONTENT_MANAGER, myManager);
        if (myManager.getContentCount() > 1) {
          sink.set(PlatformDataKeys.NONEMPTY_CONTENT_MANAGER, myManager);
        }
      }
    }
  }

  private final class MyContentManagerListener implements ContentManagerListener {
    @Override
    public void contentAdded(@NotNull ContentManagerEvent event) {
      Content content = event.getContent();
      myTabbedPaneWrapper.insertTab(content.getTabName(),
                                    content.getIcon(),
                                    content.getComponent(),
                                    content.getDescription(),
                                    event.getIndex());
      content.addPropertyChangeListener(TabbedPaneContentUI.this);
    }

    @Override
    public void contentRemoved(@NotNull ContentManagerEvent event) {
      event.getContent().removePropertyChangeListener(TabbedPaneContentUI.this);
      myTabbedPaneWrapper.removeTabAt(event.getIndex());
    }

    @Override
    public void selectionChanged(@NotNull ContentManagerEvent event) {
      int index = event.getIndex();
      if (index != -1 && event.getOperation() != ContentManagerEvent.ContentOperation.remove) {
        myTabbedPaneWrapper.setSelectedIndex(index);
      }
    }
  }

  @Override
  public boolean isSingleSelection() {
    return true;
  }

  @Override
  public boolean isToSelectAddedContent() {
    return false;
  }

  @Override
  public boolean canBeEmptySelection() {
    return false;
  }

  @Override
  public boolean canChangeSelectionTo(@NotNull Content content, boolean implicit) {
    return true;
  }

  @Override
  public @NotNull String getCloseActionName() {
    return UIBundle.message("tabbed.pane.close.tab.action.name");
  }

  @Override
  public @NotNull String getCloseAllButThisActionName() {
    return UIBundle.message("tabbed.pane.close.all.tabs.but.this.action.name");
  }

  @Override
  public @NotNull String getPreviousContentActionName() {
    return IdeBundle.message("action.text.select.previous.tab");
  }

  @Override
  public @NotNull String getNextContentActionName() {
    return IdeBundle.message("action.text.select.next.tab");
  }

}
