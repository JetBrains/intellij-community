// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.TabTitle;
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

public class TabbedPaneWrapper  {
  private static final Logger LOG = Logger.getInstance(TabbedPaneWrapper.class);
  protected TabbedPane myTabbedPane;
  private JComponent myTabbedPaneHolder;

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
  public TabbedPaneWrapper(int tabPlacement, @NotNull PrevNextActionsDescriptor installKeyboardNavigation, @NotNull Disposable parentDisposable) {
    TabFactory factory = SwingConstants.BOTTOM == tabPlacement || SwingConstants.TOP == tabPlacement
                         ? new JBTabsFactory(this, null, parentDisposable)
                         : new JTabbedPaneFactory(this);

    init(tabPlacement, installKeyboardNavigation, factory);
  }

  void init(int tabPlacement, @NotNull PrevNextActionsDescriptor installKeyboardNavigation, @NotNull TabFactory tabbedPaneFactory) {
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

  private void assertIsDispatchThread() {
    final ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    if (application != null){
      application.assertIsDispatchThread(myTabbedPane.getComponent());
    }
  }

  public final void addChangeListener(@NotNull ChangeListener listener){
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
   * @see JTabbedPane#addTab(String, Icon, Component, String)
   */
  public final synchronized void addTab(@TabTitle final String title,
                                        final Icon icon,
                                        final @NotNull JComponent component,
                                        final @NlsContexts.Tooltip String tip) {
    insertTab(title, icon, component, tip, myTabbedPane.getTabCount());
  }

  public final synchronized void addTab(@TabTitle final String title, final JComponent component) {
    if (component != null) {
      insertTab(title, null, component, null, myTabbedPane.getTabCount());
    }
    else {
      LOG.error("Unable to insert a tab without component: " + title);
    }
  }

  public synchronized void insertTab(@TabTitle final String title,
                                     @Nullable Icon icon,
                                     final @NotNull JComponent component,
                                     final @NlsContexts.Tooltip String tip,
                                     final int index) {
    myTabbedPane.insertTab(title, icon, createTabWrapper(component), tip, index);
  }

  private TabWrapper createTabWrapper(@NotNull JComponent component) {
    return myFactory.createTabWrapper(component);
  }

  protected TabbedPane createTabbedPane(final int tabPlacement) {
    return myFactory.createTabbedPane(tabPlacement);
  }

  /**
   * @see JTabbedPane#setTabPlacement
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
   * @see JTabbedPane#getSelectedComponent()
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
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() ->
              IdeFocusManager.getGlobalInstance().requestFocus(myTabbedPaneHolder, true));
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
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() ->
          IdeFocusManager.getGlobalInstance().requestFocus(myTabbedPaneHolder, true));
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
   * @see JTabbedPane#setForegroundAt(int, Color)
   */
  public final void setForegroundAt(final int index,final Color color){
    assertIsDispatchThread();
    myTabbedPane.setForegroundAt(index,color);
  }

  public final Component getTabComponentAt(final int index) {
    return myTabbedPane.getTabComponentAt(index);
  }
  /**
   * @see JTabbedPane#setComponentAt(int, Component)
   */
  public final synchronized JComponent getComponentAt(final int i) {
    return getWrapperAt(i).getComponent();
  }

  private TabWrapper getWrapperAt(final int i) {
    return (TabWrapper)myTabbedPane.getComponentAt(i);
  }

  public final void setTitleAt(final int index, @NotNull @TabTitle String title) {
    assertIsDispatchThread();
    myTabbedPane.setTitleAt(index, title);
  }

  public final void setToolTipTextAt(final int index, @NlsContexts.Tooltip String toolTipText) {
    assertIsDispatchThread();
    myTabbedPane.setToolTipTextAt(index, toolTipText);
  }

  /**
   * @see JTabbedPane#setComponentAt(int, Component)
   */
  public final synchronized void setComponentAt(final int index, final @NotNull JComponent component) {
    assertIsDispatchThread();
    myTabbedPane.setComponentAt(index, createTabWrapper(component));
  }

  /**
   * @see JTabbedPane#setIconAt(int, Icon)
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
   * @see JTabbedPane#indexOfComponent(Component)
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
   * @see JTabbedPane#getTabLayoutPolicy
   */
  public final synchronized int getTabLayoutPolicy(){
    return myTabbedPane.getTabLayoutPolicy();
  }

  /**
   * @see JTabbedPane#setTabLayoutPolicy
   */
  public final synchronized void setTabLayoutPolicy(final int policy){
    myTabbedPane.setTabLayoutPolicy(policy);
    final int index=myTabbedPane.getSelectedIndex();
    if(index!=-1){
      myTabbedPane.scrollTabToVisible(index);
    }
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

  @NotNull
  public TabbedPane getTabbedPane() {
    return myTabbedPane;
  }

  public static final class TabWrapper extends JPanel implements DataProvider {
    private JComponent myComponent;

    boolean myCustomFocus = true;

    TabWrapper(@NotNull final JComponent component) {
      super(new BorderLayout());
      myComponent = component;
      add(component, BorderLayout.CENTER);
    }

    /*
     * Make possible to search down for DataProviders
     */
    @Override
    public Object getData(@NotNull final String dataId) {
      if(myComponent instanceof DataProvider){
        return ((DataProvider)myComponent).getData(dataId);
      }
      return null;
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

    @Override
    public boolean requestDefaultFocus() {
      if (!myCustomFocus) return super.requestDefaultFocus();
      if (myComponent == null) return false; // Just in case someone requests the focus when we're already removed from the Swing tree.
      final JComponent preferredFocusedComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent(myComponent);
      if (preferredFocusedComponent != null) {
        if (!preferredFocusedComponent.requestFocusInWindow()) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() ->
            IdeFocusManager.getGlobalInstance().requestFocus(preferredFocusedComponent, true));
        }
        return true;
      }
      return myComponent.requestDefaultFocus();
    }

    @Override
    public void requestFocus() {
      if (!myCustomFocus) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> super.requestFocus());
      }
      else {
        requestDefaultFocus();
      }
    }

    @Override
    public boolean requestFocusInWindow() {
      if (!myCustomFocus) return super.requestFocusInWindow();
      return requestDefaultFocus();
    }
  }

  private final class _MyFocusTraversalPolicy extends IdeFocusTraversalPolicy{
    @Override
    public Component getDefaultComponent(final Container focusCycleRoot) {
      final JComponent component=getSelectedComponent();
      return component == null ? null : IdeFocusTraversalPolicy.getPreferredFocusedComponent(component, this);
    }
  }

  public static class TabbedPaneHolder extends JPanel {

    private final TabbedPaneWrapper myWrapper;

    protected TabbedPaneHolder(TabbedPaneWrapper wrapper) {
      super(new BorderLayout());
      myWrapper = wrapper;
    }

    @Override
    public boolean requestDefaultFocus() {
      final JComponent preferredFocusedComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent(myWrapper.myTabbedPane.getComponent());
      if (preferredFocusedComponent != null) {
        if (!preferredFocusedComponent.requestFocusInWindow()) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() ->
            IdeFocusManager.getGlobalInstance().requestFocus(preferredFocusedComponent, true));
        }
        return true;
      }
      return super.requestDefaultFocus();
    }

    @Override
    public final void requestFocus() {
      requestDefaultFocus();
    }

    @Override
    public final boolean requestFocusInWindow() {
      return requestDefaultFocus();
    }

    @Override
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
    @NotNull
    TabbedPane createTabbedPane(int tabPlacement);
    @NotNull
    TabbedPaneHolder createTabbedPaneHolder();
    @NotNull
    TabWrapper createTabWrapper(@NotNull JComponent component);
  }

  private static final class JTabbedPaneFactory implements TabFactory {
    private final TabbedPaneWrapper myWrapper;

    private JTabbedPaneFactory(TabbedPaneWrapper wrapper) {
      myWrapper = wrapper;
    }

    @NotNull
    @Override
    public TabbedPane createTabbedPane(int tabPlacement) {
      return new TabbedPaneImpl(tabPlacement);
    }

    @NotNull
    @Override
    public TabbedPaneHolder createTabbedPaneHolder() {
      return new TabbedPaneHolder(myWrapper);
    }

    @NotNull
    @Override
    public TabWrapper createTabWrapper(@NotNull JComponent component) {
      return new TabWrapper(component);
    }
  }

  private static final class JBTabsFactory implements TabFactory {

    private final Project myProject;
    private final Disposable myParent;
    private final TabbedPaneWrapper myWrapper;

    private JBTabsFactory(TabbedPaneWrapper wrapper, Project project, @NotNull Disposable parent) {
      myWrapper = wrapper;
      myProject = project;
      myParent = parent;
    }

    @NotNull
    @Override
    public TabbedPane createTabbedPane(int tabPlacement) {
      return new JBTabsPaneImpl(myProject, tabPlacement, myParent);
    }

    @NotNull
    @Override
    public TabbedPaneHolder createTabbedPaneHolder() {
      return new TabbedPaneHolder(myWrapper) {
        @Override
        public boolean requestDefaultFocus() {
          getTabs().requestFocus();
          return true;
        }

      };
    }

    @NotNull
    @Override
    public TabWrapper createTabWrapper(@NotNull JComponent component) {
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

    @NotNull
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
