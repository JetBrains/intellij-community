/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.util.IJSwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class TabbedPaneWrapper  {
  protected TabbedPane myTabbedPane;
  protected JComponent myTabbedPaneHolder;

  private TabFactory myFactory;

  protected TabbedPaneWrapper(boolean construct) {
    if (construct) {
      init(SwingConstants.TOP, TabbedPaneImpl.DEFAULT_PREV_NEXT_SHORTCUTS, new JTabbedPaneFactory(this));
    }
  }

  public TabbedPaneWrapper(@NotNull Disposable parentDisposable) {
    this(SwingConstants.TOP, TabbedPaneImpl.DEFAULT_PREV_NEXT_SHORTCUTS, parentDisposable);
  }

  /**
   * Creates tabbed pane wrapper with specified tab placement
   *
   * @param tabPlacement tab placement. It one of the {@code SwingConstants.TOP},
   * {@code SwingConstants.LEFT}, {@code SwingConstants.BOTTOM} or
   * {@code SwingConstants.RIGHT}.
   */
  public TabbedPaneWrapper(int tabPlacement, PrevNextActionsDescriptor installKeyboardNavigation, @NotNull Disposable parentDisposable) {
    final TabFactory factory;
    if (SwingConstants.BOTTOM == tabPlacement || SwingConstants.TOP == tabPlacement) {
      factory = new JBTabsFactory(this, null, parentDisposable);
    } else {
      factory = new JTabbedPaneFactory(this);
    }

    init(tabPlacement, installKeyboardNavigation, factory);
  }

  void init(int tabPlacement, PrevNextActionsDescriptor installKeyboardNavigation, TabFactory tabbedPaneFactory) {
    myFactory = tabbedPaneFactory;

    myTabbedPane = createTabbedPane(tabPlacement);
    myTabbedPane.putClientProperty(TabbedPaneWrapper.class, myTabbedPane);
    myTabbedPane.setKeyboardNavigation(installKeyboardNavigation);

    myTabbedPaneHolder = createTabbedPaneHolder();
    myTabbedPaneHolder.add(myTabbedPane.getComponent(), BorderLayout.CENTER);
    myTabbedPaneHolder.setFocusTraversalPolicyProvider(true);
    myTabbedPaneHolder.setFocusTraversalPolicy(new _MyFocusTraversalPolicy());

    assertIsDispatchThread();
  }

  public boolean isDisposed() {
    return myTabbedPane != null && myTabbedPane.isDisposed();
  }

  private void assertIsDispatchThread() {
    final ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    if (application != null){
      application.assertIsDispatchThread(myTabbedPane.getComponent());
    }
  }

  public final void addChangeListener(final ChangeListener listener){
    assertIsDispatchThread();
    myTabbedPane.addChangeListener(listener);
  }

  public final void removeChangeListener(final ChangeListener listener){
    assertIsDispatchThread();
    myTabbedPane.removeChangeListener(listener);
  }

  protected TabbedPaneHolder createTabbedPaneHolder() {
    return myFactory.createTabbedPaneHolder();
  }

  public final JComponent getComponent() {
    assertIsDispatchThread();
    return myTabbedPaneHolder;
  }

  /**
   * @see javax.swing.JTabbedPane#addTab(java.lang.String, javax.swing.Icon, java.awt.Component, java.lang.String)
   */
  public final synchronized void addTab(final String title, final Icon icon, final JComponent component, final String tip) {
    insertTab(title, icon, component, tip, myTabbedPane.getTabCount());
  }

  public final synchronized void addTab(final String title, final JComponent component) {
    insertTab(title, null, component, null, myTabbedPane.getTabCount());
  }

  public synchronized void insertTab(final String title, final Icon icon, final JComponent component, final String tip, final int index) {
    myTabbedPane.insertTab(title, icon, createTabWrapper(component), tip, index);
  }

  protected TabWrapper createTabWrapper(JComponent component) {
    return myFactory.createTabWrapper(component);
  }

  protected TabbedPane createTabbedPane(final int tabPlacement) {
    return myFactory.createTabbedPane(tabPlacement);
  }

  /**
   * @see javax.swing.JTabbedPane#setTabPlacement
   */
  public final void setTabPlacement(final int tabPlacement) {
    assertIsDispatchThread();
    myTabbedPane.setTabPlacement(tabPlacement);
  }

  public final void addMouseListener(final MouseListener listener) {
    assertIsDispatchThread();
    myTabbedPane.addMouseListener(listener);
  }

  public final synchronized int getSelectedIndex() {
    return myTabbedPane.getSelectedIndex();
  }

  /**
   * @see javax.swing.JTabbedPane#getSelectedComponent()
   */
  public final synchronized JComponent getSelectedComponent() {
    // Workaround for JDK 6 bug
    final TabWrapper tabWrapper = myTabbedPane.getTabCount() > 0 ? (TabWrapper)myTabbedPane.getSelectedComponent():null;
    return tabWrapper != null ? tabWrapper.getComponent() : null;
  }

  public final void setSelectedIndex(final int index) {
    setSelectedIndex(index, true);
  }

  public final void setSelectedIndex(final int index, boolean requestFocus) {
    assertIsDispatchThread();

    final boolean hadFocus = IJSwingUtilities.hasFocus2(myTabbedPaneHolder);
    myTabbedPane.setSelectedIndex(index);
    if (hadFocus && requestFocus) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
        IdeFocusManager.getGlobalInstance().requestFocus(myTabbedPaneHolder, true);
      });
    }
  }

  public final void setSelectedComponent(final JComponent component){
    assertIsDispatchThread();

    final int index=indexOfComponent(component);
    if(index==-1){
      throw new IllegalArgumentException("component not found in tabbed pane wrapper");
    }
    setSelectedIndex(index);
  }

  public final synchronized void removeTabAt(final int index) {
    assertIsDispatchThread();

    final boolean hadFocus = IJSwingUtilities.hasFocus2(myTabbedPaneHolder);
    final TabWrapper wrapper = getWrapperAt(index);
    try {
      myTabbedPane.removeTabAt(index);
      if (myTabbedPane.getTabCount() == 0) {
        // to clear BasicTabbedPaneUI.visibleComponent field
        myTabbedPane.revalidate();
      }
      if (hadFocus) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
          IdeFocusManager.getGlobalInstance().requestFocus(myTabbedPaneHolder, true);
        });
      }
    }
    finally {
      wrapper.dispose();
    }
  }

  public final synchronized int getTabCount() {
    return myTabbedPane.getTabCount();
  }

  public final Color getForegroundAt(final int index){
    assertIsDispatchThread();
    return myTabbedPane.getForegroundAt(index);
  }

  /**
   * @see javax.swing.JTabbedPane#setForegroundAt(int, java.awt.Color)
   */
  public final void setForegroundAt(final int index,final Color color){
    assertIsDispatchThread();
    myTabbedPane.setForegroundAt(index,color);
  }

  public final Component getTabComponentAt(final int index) {
    return myTabbedPane.getTabComponentAt(index);
  }
  /**
   * @see javax.swing.JTabbedPane#setComponentAt(int, java.awt.Component)
   */
  public final synchronized JComponent getComponentAt(final int i) {
    return getWrapperAt(i).getComponent();
  }

  private TabWrapper getWrapperAt(final int i) {
    return (TabWrapper)myTabbedPane.getComponentAt(i);
  }

  public final void setTitleAt(final int index, final String title) {
    assertIsDispatchThread();
    myTabbedPane.setTitleAt(index, title);
  }

  public final void setToolTipTextAt(final int index, final String toolTipText) {
    assertIsDispatchThread();
    myTabbedPane.setToolTipTextAt(index, toolTipText);
  }

  /**
   * @see javax.swing.JTabbedPane#setComponentAt(int, java.awt.Component)
   */
  public final synchronized void setComponentAt(final int index, final JComponent component) {
    assertIsDispatchThread();
    myTabbedPane.setComponentAt(index, createTabWrapper(component));
  }

  /**
   * @see javax.swing.JTabbedPane#setIconAt(int, javax.swing.Icon)
   */
  public final void setIconAt(final int index, final Icon icon) {
    assertIsDispatchThread();
    myTabbedPane.setIconAt(index, icon);
  }

  public final void setEnabledAt(final int index, final boolean enabled) {
    assertIsDispatchThread();
    myTabbedPane.setEnabledAt(index, enabled);
  }

  /**
   * @see javax.swing.JTabbedPane#indexOfComponent(java.awt.Component)
   */
  public final synchronized int indexOfComponent(final JComponent component) {
    for (int i=0; i < myTabbedPane.getTabCount(); i++) {
      final JComponent c = getWrapperAt(i).getComponent();
      if (c == component) {
        return i;
      }
    }
    return -1;
  }

  /**
   * @see javax.swing.JTabbedPane#getTabLayoutPolicy
   */
  public final synchronized int getTabLayoutPolicy(){
    return myTabbedPane.getTabLayoutPolicy();
  }

  /**
   * @see javax.swing.JTabbedPane#setTabLayoutPolicy
   */
  public final synchronized void setTabLayoutPolicy(final int policy){
    myTabbedPane.setTabLayoutPolicy(policy);
    final int index=myTabbedPane.getSelectedIndex();
    if(index!=-1){
      myTabbedPane.scrollTabToVisible(index);
    }
  }

  /**
   * @deprecated Keyboard navigation is installed/deinstalled automatically. This method does nothing now.
   */
  public final void installKeyboardNavigation(){
  }

  /**
   * @deprecated Keyboard navigation is installed/deinstalled automatically. This method does nothing now.
   */
  public final void uninstallKeyboardNavigation(){
  }

  public final String getTitleAt(final int i) {
    return myTabbedPane.getTitleAt(i);
  }

  public void setSelectedTitle(@Nullable final String title) {
    if (title == null) return;

    for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
      final String each = myTabbedPane.getTitleAt(i);
      if (title.equals(each)) {
        myTabbedPane.setSelectedIndex(i);
        break;
      }
    }
  }

  @Nullable
  public String getSelectedTitle() {
    return getSelectedIndex() < 0 ? null : getTitleAt(getSelectedIndex());
  }

  public void removeAll() {
    myTabbedPane.removeAll();
  }

  public static final class TabWrapper extends JPanel implements DataProvider{
    private JComponent myComponent;

    boolean myCustomFocus = true;

    public TabWrapper(@NotNull final JComponent component) {
      super(new BorderLayout());
      myComponent = component;
      add(component, BorderLayout.CENTER);
    }

    /*
     * Make possible to search down for DataProviders
     */
    public Object getData(final String dataId) {
      if(myComponent instanceof DataProvider){
        return ((DataProvider)myComponent).getData(dataId);
      } else {
        return null;
      }
    }

    public JComponent getComponent() {
      return myComponent;
    }

    /**
     * TabWrappers are never reused so we can fix the leak in some LAF's TabbedPane UI by cleanuping ourselves.
     */
    public void dispose() {
      if (myComponent != null) {
        remove(myComponent);
        myComponent = null;
      }
    }

    public boolean requestDefaultFocus() {
      if (!myCustomFocus) return super.requestDefaultFocus();
      if (myComponent == null) return false; // Just in case someone requests the focus when we're already removed from the Swing tree.
      final JComponent preferredFocusedComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent(myComponent);
      if (preferredFocusedComponent != null) {
        if (!preferredFocusedComponent.requestFocusInWindow()) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
            IdeFocusManager.getGlobalInstance().requestFocus(preferredFocusedComponent, true);
          });
        }
        return true;
      } else {
        return myComponent.requestDefaultFocus();
      }
    }

    public void requestFocus() {
      if (!myCustomFocus) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
          super.requestFocus();
        });
      } else {
        requestDefaultFocus();
      }
    }

    public boolean requestFocusInWindow() {
      if (!myCustomFocus) return super.requestFocusInWindow();
      return requestDefaultFocus();
    }
  }

  private final class _MyFocusTraversalPolicy extends IdeFocusTraversalPolicy{
    @Override
    public boolean isNoDefaultComponent() {
      return false;
    }

    public final Component getDefaultComponentImpl(final Container focusCycleRoot) {
      final JComponent component=getSelectedComponent();
      if(component!=null){
        return IdeFocusTraversalPolicy.getPreferredFocusedComponent(component, this);
      }else{
        return null;
      }
    }
  }

  public static class TabbedPaneHolder extends JPanel {

    private final TabbedPaneWrapper myWrapper;

    protected TabbedPaneHolder(TabbedPaneWrapper wrapper) {
      super(new BorderLayout());
      myWrapper = wrapper;
    }

    public boolean requestDefaultFocus() {
      final JComponent preferredFocusedComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent(myWrapper.myTabbedPane.getComponent());
      if (preferredFocusedComponent != null) {
        if (!preferredFocusedComponent.requestFocusInWindow()) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
            IdeFocusManager.getGlobalInstance().requestFocus(preferredFocusedComponent, true);
          });
        }
        return true;
      } else {
        return super.requestDefaultFocus();
      }
    }

    public final void requestFocus() {
      requestDefaultFocus();
    }

    public final boolean requestFocusInWindow() {
      return requestDefaultFocus();
    }

    public void updateUI() {
      super.updateUI();
      if (myWrapper != null) {
        myWrapper.myTabbedPane.updateUI();
      }
    }

    public TabbedPaneWrapper getTabbedPaneWrapper() {
      return myWrapper;
    }
  }

  public static TabbedPaneWrapper get(JTabbedPane tabs) {
    return (TabbedPaneWrapper)tabs.getClientProperty(TabbedPaneWrapper.class);
  }

  private interface TabFactory {
    TabbedPane createTabbedPane(int tabPlacement);
    TabbedPaneHolder createTabbedPaneHolder();
    TabWrapper createTabWrapper(JComponent component);
  }

  private static class JTabbedPaneFactory implements TabFactory {
    private final TabbedPaneWrapper myWrapper;

    private JTabbedPaneFactory(TabbedPaneWrapper wrapper) {
      myWrapper = wrapper;
    }

    public TabbedPane createTabbedPane(int tabPlacement) {
      return new TabbedPaneImpl(tabPlacement);
    }

    public TabbedPaneHolder createTabbedPaneHolder() {
      return new TabbedPaneHolder(myWrapper);
    }

    public TabWrapper createTabWrapper(JComponent component) {
      return new TabWrapper(component);
    }
  }

  private static class JBTabsFactory implements TabFactory {

    private final Project myProject;
    private final Disposable myParent;
    private final TabbedPaneWrapper myWrapper;

    private JBTabsFactory(TabbedPaneWrapper wrapper, Project project, @NotNull Disposable parent) {
      myWrapper = wrapper;
      myProject = project;
      myParent = parent;
    }

    public TabbedPane createTabbedPane(int tabPlacement) {
      return new JBTabsPaneImpl(myProject, tabPlacement, myParent);
    }

    public TabbedPaneHolder createTabbedPaneHolder() {
      return new TabbedPaneHolder(myWrapper) {
        @Override
        public boolean requestDefaultFocus() {
          getTabs().requestFocus();
          return true;
        }

      };
    }

    public TabWrapper createTabWrapper(JComponent component) {
      final TabWrapper tabWrapper = new TabWrapper(component);
      tabWrapper.myCustomFocus = false;
      return tabWrapper;
    }

    public JBTabs getTabs() {
      return ((JBTabsPaneImpl)myWrapper.myTabbedPane).getTabs();
    }

    public void dispose() {
    }
  }

  public static class AsJBTabs extends TabbedPaneWrapper {
    public AsJBTabs(@Nullable Project project, int tabPlacement, PrevNextActionsDescriptor installKeyboardNavigation, @NotNull Disposable parent) {
      super(false);
      init(tabPlacement, installKeyboardNavigation, new JBTabsFactory(this, project, parent));
    }

    public JBTabs getTabs() {
      return ((JBTabsPaneImpl)myTabbedPane).getTabs();
    }
  }

  public static class AsJTabbedPane extends TabbedPaneWrapper {
    public AsJTabbedPane(int tabPlacement) {
      super(false);
      init(tabPlacement, TabbedPaneImpl.DEFAULT_PREV_NEXT_SHORTCUTS, new JTabbedPaneFactory(this));
    }
  }

}
