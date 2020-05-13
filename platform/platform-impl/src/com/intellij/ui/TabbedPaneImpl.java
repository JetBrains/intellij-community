// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBTabbedPane;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.TabbedPaneUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class TabbedPaneImpl extends JBTabbedPane implements TabbedPane {

  public static final PrevNextActionsDescriptor DEFAULT_PREV_NEXT_SHORTCUTS = new PrevNextActionsDescriptor(IdeActions.ACTION_NEXT_TAB,
                                                                                                            IdeActions.ACTION_PREVIOUS_TAB);

  private static final Logger LOG = Logger.getInstance(TabbedPaneImpl.class);

  private ScrollableTabSupport myScrollableTabSupport;
  private AnAction myNextTabAction;
  private AnAction myPreviousTabAction;
  private PrevNextActionsDescriptor myInstallKeyboardNavigation;

  public TabbedPaneImpl(@JdkConstants.TabPlacement int tabPlacement) {
    super(tabPlacement);
    setFocusable(false);
    addMouseListener(
      new MouseAdapter() {
        @Override
        public void mousePressed(final MouseEvent e) {
          _requestDefaultFocus();
        }
      }
    );
  }

  @Override
  public void setKeyboardNavigation(@NotNull PrevNextActionsDescriptor installKeyboardNavigation) {
    myInstallKeyboardNavigation = installKeyboardNavigation;
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (myInstallKeyboardNavigation != null) {
      installKeyboardNavigation(myInstallKeyboardNavigation);
    }
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    if (myInstallKeyboardNavigation != null) {
      uninstallKeyboardNavigation();
    }
  }

  private void installKeyboardNavigation(final PrevNextActionsDescriptor installKeyboardNavigation){
    myNextTabAction = new AnAction() {
      {
        setEnabledInModalContext(true);
      }

      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        int index = getSelectedIndex() + 1;
        if (index >= getTabCount()) {
          index = 0;
        }
        setSelectedIndex(index);
      }
    };
    final AnAction nextAction = ActionManager.getInstance().getAction(installKeyboardNavigation.getNextActionId());
    LOG.assertTrue(nextAction != null, "Cannot find action with specified id: " + installKeyboardNavigation.getNextActionId());
    myNextTabAction.registerCustomShortcutSet(nextAction.getShortcutSet(), this);

    myPreviousTabAction = new AnAction() {
      {
        setEnabledInModalContext(true);
      }

      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        int index = getSelectedIndex() - 1;
        if (index < 0) {
          index = getTabCount() - 1;
        }
        setSelectedIndex(index);
      }
    };
    final AnAction prevAction = ActionManager.getInstance().getAction(installKeyboardNavigation.getPrevActionId());
    LOG.assertTrue(prevAction != null, "Cannot find action with specified id: " + installKeyboardNavigation.getPrevActionId());
    myPreviousTabAction.registerCustomShortcutSet(prevAction.getShortcutSet(), this);
  }

  private void uninstallKeyboardNavigation() {
    if (myNextTabAction != null) {
      myNextTabAction.unregisterCustomShortcutSet(this);
      myNextTabAction = null;
    }
    if (myPreviousTabAction != null) {
      myPreviousTabAction.unregisterCustomShortcutSet(this);
      myPreviousTabAction = null;
    }
  }


  @Override
  public void setUI(final TabbedPaneUI ui){
    super.setUI(ui);
    myScrollableTabSupport = ui instanceof BasicTabbedPaneUI ? new ScrollableTabSupport((BasicTabbedPaneUI)ui) : null;
  }

  /**
   * Scrolls tab to visible area. If tabbed pane has {@code JTabbedPane.WRAP_TAB_LAYOUT} layout policy then
   * the method does nothing.
   * @param index index of tab to be scrolled.
   */
  @Override
  public final void scrollTabToVisible(final int index){
    if(myScrollableTabSupport==null|| WRAP_TAB_LAYOUT==getTabLayoutPolicy()){ // tab scrolling isn't supported by UI
      return;
    }
    final TabbedPaneUI tabbedPaneUI=getUI();
    Rectangle tabBounds=tabbedPaneUI.getTabBounds(this,index);
    final int tabPlacement=getTabPlacement();
    if(TOP==tabPlacement || BOTTOM==tabPlacement){ //tabs are on the top or bottom
      if(tabBounds.x<50){  //if tab is to the left of visible area
        int leadingTabIndex=myScrollableTabSupport.getLeadingTabIndex();
        while(leadingTabIndex != index && leadingTabIndex>0 && tabBounds.x<50){
          myScrollableTabSupport.setLeadingTabIndex(leadingTabIndex-1);
          leadingTabIndex=myScrollableTabSupport.getLeadingTabIndex();
          tabBounds=tabbedPaneUI.getTabBounds(this,index);
        }
      }else if(tabBounds.x+tabBounds.width>getWidth()-50){ // if tab's right side is out of visible range
        int leadingTabIndex=myScrollableTabSupport.getLeadingTabIndex();
        while(leadingTabIndex != index && leadingTabIndex<getTabCount()-1 && tabBounds.x+tabBounds.width>getWidth()-50){
          myScrollableTabSupport.setLeadingTabIndex(leadingTabIndex+1);
          leadingTabIndex=myScrollableTabSupport.getLeadingTabIndex();
          tabBounds=tabbedPaneUI.getTabBounds(this,index);
        }
      }
    }else{ // tabs are on left or right side
      if(tabBounds.y<30){ //tab is above visible area
        int leadingTabIndex=myScrollableTabSupport.getLeadingTabIndex();
        while(leadingTabIndex != index && leadingTabIndex>0 && tabBounds.y<30){
          myScrollableTabSupport.setLeadingTabIndex(leadingTabIndex-1);
          leadingTabIndex=myScrollableTabSupport.getLeadingTabIndex();
          tabBounds=tabbedPaneUI.getTabBounds(this,index);
        }
      } else if(tabBounds.y+tabBounds.height>getHeight()-30){  //tab is under visible area
        int leadingTabIndex=myScrollableTabSupport.getLeadingTabIndex();
        while(leadingTabIndex != index && leadingTabIndex<getTabCount()-1 && tabBounds.y+tabBounds.height>getHeight()-30){
          myScrollableTabSupport.setLeadingTabIndex(leadingTabIndex+1);
          leadingTabIndex=myScrollableTabSupport.getLeadingTabIndex();
          tabBounds=tabbedPaneUI.getTabBounds(this,index);
        }
      }
    }
  }

  @Override
  public void setSelectedIndex(final int index){
    if (index >= getTabCount()) return;

    try {
      super.setSelectedIndex(index);
    }
    catch (ArrayIndexOutOfBoundsException e) {
      //http://www.jetbrains.net/jira/browse/IDEADEV-22331
      //may happen on Mac OSX 10.5
      return;
    }

    scrollTabToVisible(index);
    doLayout();
  }

 //http://www.jetbrains.net/jira/browse/IDEADEV-22331
 //to let repaint happen since AIOBE is thrown from Mac OSX's UI
 @Override
 protected void fireStateChanged() {
   // Guaranteed to return a non-null array
   Object[] listeners = listenerList.getListenerList();
   // Process the listeners last to first, notifying
   // those that are interested in this event
   for (int i = listeners.length - 2; i >= 0; i -= 2) {
     if (listeners[i] == ChangeListener.class) {
       // Lazily create the event:
       if (changeEvent == null) changeEvent = new ChangeEvent(this);
       final ChangeListener each = (ChangeListener)listeners[i + 1];
       if (each != null) {
         if (each.getClass().getName().contains("apple.laf.CUIAquaTabbedPane")) {

           //noinspection SSBasedInspection
           SwingUtilities.invokeLater(() -> {
             revalidate();
             repaint();
           });

           continue;
         }

         each.stateChanged(changeEvent);
       }
     }
   }
 }


  @Override
  public final void removeTabAt (final int index) {
    super.removeTabAt (index);
    //This event should be fired necessarily because when swing fires an event
    // page to be removed is still in the tabbed pane. There can be a situation when
    // event fired according to swing event contains invalid information about selected page.
    fireStateChanged();
  }

  private void _requestDefaultFocus() {
    final Component selectedComponent = getSelectedComponent();
    if (selectedComponent instanceof TabbedPaneWrapper.TabWrapper) {
      ((TabbedPaneWrapper.TabWrapper)selectedComponent).requestDefaultFocus();
    }
    else {
      super.requestDefaultFocus();
    }
  }

  protected final int getTabIndexAt(final int x,final int y){
    final TabbedPaneUI ui=getUI();
    for (int i = 0; i < getTabCount(); i++) {
      final Rectangle bounds = ui.getTabBounds(this, i);
      if (bounds.contains(x, y)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * That is hack-helper for working with scrollable tab layout. The problem is BasicTabbedPaneUI doesn't
   * have any API to scroll tab to visible area. Therefore we have to implement it...
   */
  private final class ScrollableTabSupport{
    private final BasicTabbedPaneUI myUI;
    @NonNls static final String TAB_SCROLLER_NAME = "tabScroller";
    @NonNls static final String LEADING_TAB_INDEX_NAME = "leadingTabIndex";
    @NonNls static final String SET_LEADING_TAB_INDEX_METHOD = "setLeadingTabIndex";

    ScrollableTabSupport(final BasicTabbedPaneUI ui){
      myUI=ui;
    }

    /**
     * @return value of {@code leadingTabIndex} field of BasicTabbedPaneUI.ScrollableTabSupport class.
     */
    int getLeadingTabIndex() {
      try {
        final Field tabScrollerField = BasicTabbedPaneUI.class.getDeclaredField(TAB_SCROLLER_NAME);
        tabScrollerField.setAccessible(true);
        final Object tabScrollerValue = tabScrollerField.get(myUI);

        final Field leadingTabIndexField = tabScrollerValue.getClass().getDeclaredField(LEADING_TAB_INDEX_NAME);
        leadingTabIndexField.setAccessible(true);
        return leadingTabIndexField.getInt(tabScrollerValue);
      }
      catch (Exception exc) {
        final String writer = StringUtil.getThrowableText(exc);
        throw new IllegalStateException("myUI=" + myUI + "; cause=" + writer);
      }
    }

    void setLeadingTabIndex(final int leadingIndex) {
      try {
        final Field tabScrollerField = BasicTabbedPaneUI.class.getDeclaredField(TAB_SCROLLER_NAME);
        tabScrollerField.setAccessible(true);
        final Object tabScrollerValue = tabScrollerField.get(myUI);

        Method setLeadingIndexMethod = null;
        final Method[] methods = tabScrollerValue.getClass().getDeclaredMethods();
        for (final Method method : methods) {
          if (SET_LEADING_TAB_INDEX_METHOD.equals(method.getName())) {
            setLeadingIndexMethod = method;
            break;
          }
        }
        if (setLeadingIndexMethod == null) {
          throw new IllegalStateException("method setLeadingTabIndex not found");
        }
        setLeadingIndexMethod.setAccessible(true);
        setLeadingIndexMethod.invoke(tabScrollerValue, getTabPlacement(), leadingIndex);
      }
      catch (Exception exc) {
        final String writer = StringUtil.getThrowableText(exc);
        throw new IllegalStateException("myUI=" + myUI + "; cause=" + writer);
      }
    }
  }
}
