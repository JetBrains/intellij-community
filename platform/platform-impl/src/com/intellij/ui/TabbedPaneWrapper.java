// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiCompatibleDataProvider;
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
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;

public class TabbedPaneWrapper {
  private static final Logger LOG = Logger.getInstance(TabbedPaneWrapper.class);
  protected TabbedPane tabbedPane;
  private JComponent tabbedPaneHolder;

  private TabFactory factory;

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
   *                     {@code SwingConstants.LEFT}, {@code SwingConstants.BOTTOM} or
   *                     {@code SwingConstants.RIGHT}.
   */
  public TabbedPaneWrapper(int tabPlacement,
                           @NotNull PrevNextActionsDescriptor installKeyboardNavigation,
                           @NotNull Disposable parentDisposable) {
    TabFactory factory = SwingConstants.BOTTOM == tabPlacement || SwingConstants.TOP == tabPlacement
                         ? new JBTabsFactory(this, null, parentDisposable)
                         : new JTabbedPaneFactory(this);

    init(tabPlacement, installKeyboardNavigation, factory);
  }

  final void init(int tabPlacement, @NotNull PrevNextActionsDescriptor installKeyboardNavigation, @NotNull TabFactory tabbedPaneFactory) {
    factory = tabbedPaneFactory;

    tabbedPane = createTabbedPane(tabPlacement);
    tabbedPane.putClientProperty(TabbedPaneWrapper.class, tabbedPane);
    tabbedPane.setKeyboardNavigation(installKeyboardNavigation);

    tabbedPaneHolder = createTabbedPaneHolder();
    tabbedPaneHolder.add(tabbedPane.getComponent(), BorderLayout.CENTER);
    tabbedPaneHolder.setFocusTraversalPolicyProvider(true);
    tabbedPaneHolder.setFocusTraversalPolicy(new _MyFocusTraversalPolicy());

    assertIsDispatchThread();
  }

  private void assertIsDispatchThread() {
    ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    if (application != null) {
      application.assertIsDispatchThread(tabbedPane.getComponent());
    }
  }

  public final void addChangeListener(@NotNull ChangeListener listener) {
    assertIsDispatchThread();
    tabbedPane.addChangeListener(listener);
  }

  public final void removeChangeListener(final ChangeListener listener) {
    assertIsDispatchThread();
    tabbedPane.removeChangeListener(listener);
  }

  protected TabbedPaneHolder createTabbedPaneHolder() {
    return factory.createTabbedPaneHolder();
  }

  public final JComponent getComponent() {
    assertIsDispatchThread();
    return tabbedPaneHolder;
  }

  /**
   * @see JTabbedPane#addTab(String, Icon, Component, String)
   */
  public final synchronized void addTab(final @TabTitle String title,
                                        final Icon icon,
                                        final @NotNull JComponent component,
                                        final @NlsContexts.Tooltip String tip) {
    insertTab(title, icon, component, tip, tabbedPane.getTabCount());
  }

  public final synchronized void addTab(@TabTitle String title, @Nullable JComponent component) {
    if (component == null) {
      LOG.error("Unable to insert a tab without component: " + title);
    }
    else {
      insertTab(title, null, component, null, tabbedPane.getTabCount());
    }
  }

  public synchronized void insertTab(final @TabTitle String title,
                                     @Nullable Icon icon,
                                     final @NotNull JComponent component,
                                     final @NlsContexts.Tooltip String tip,
                                     final int index) {
    tabbedPane.insertTab(title, icon, createTabWrapper(component), tip, index);
  }

  private TabWrapper createTabWrapper(@NotNull JComponent component) {
    return factory.createTabWrapper(component);
  }

  protected TabbedPane createTabbedPane(final int tabPlacement) {
    return factory.createTabbedPane(tabPlacement);
  }

  /**
   * @see JTabbedPane#setTabPlacement
   */
  public final void setTabPlacement(final int tabPlacement) {
    assertIsDispatchThread();
    tabbedPane.setTabPlacement(tabPlacement);
  }

  public final void addMouseListener(final MouseListener listener) {
    assertIsDispatchThread();
    tabbedPane.addMouseListener(listener);
  }

  public final synchronized int getSelectedIndex() {
    return tabbedPane.getSelectedIndex();
  }

  public final void setSelectedIndex(final int index) {
    setSelectedIndex(index, true);
  }

  /**
   * @see JTabbedPane#getSelectedComponent()
   */
  public final synchronized JComponent getSelectedComponent() {
    // Workaround for JDK 6 bug
    final TabWrapper tabWrapper = tabbedPane.getTabCount() > 0 ? (TabWrapper)tabbedPane.getSelectedComponent() : null;
    return tabWrapper != null ? tabWrapper.getComponent() : null;
  }

  public final void setSelectedComponent(final JComponent component) {
    assertIsDispatchThread();

    final int index = indexOfComponent(component);
    if (index == -1) {
      throw new IllegalArgumentException("component not found in tabbed pane wrapper");
    }
    setSelectedIndex(index);
  }

  public final void setSelectedIndex(final int index, boolean requestFocus) {
    assertIsDispatchThread();

    final boolean hadFocus = IJSwingUtilities.hasFocus2(tabbedPaneHolder);
    tabbedPane.setSelectedIndex(index);
    if (hadFocus && requestFocus) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() ->
                                                                   IdeFocusManager.getGlobalInstance()
                                                                     .requestFocus(tabbedPaneHolder, true));
    }
  }

  public final synchronized void removeTabAt(final int index) {
    assertIsDispatchThread();

    final boolean hadFocus = IJSwingUtilities.hasFocus2(tabbedPaneHolder);
    final TabWrapper wrapper = getWrapperAt(index);
    try {
      tabbedPane.removeTabAt(index);
      if (tabbedPane.getTabCount() == 0) {
        // to clear BasicTabbedPaneUI.visibleComponent field
        tabbedPane.revalidate();
      }
      if (hadFocus) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() ->
                                                                     IdeFocusManager.getGlobalInstance()
                                                                       .requestFocus(tabbedPaneHolder, true));
      }
    }
    finally {
      wrapper.dispose();
    }
  }

  public final synchronized int getTabCount() {
    return tabbedPane.getTabCount();
  }

  public final Color getForegroundAt(final int index) {
    assertIsDispatchThread();
    return tabbedPane.getForegroundAt(index);
  }

  /**
   * @see JTabbedPane#setForegroundAt(int, Color)
   */
  public final void setForegroundAt(final int index, final Color color) {
    assertIsDispatchThread();
    tabbedPane.setForegroundAt(index, color);
  }

  public final Component getTabComponentAt(final int index) {
    return tabbedPane.getTabComponentAt(index);
  }

  /**
   * @see JTabbedPane#setComponentAt(int, Component)
   */
  public final synchronized JComponent getComponentAt(final int i) {
    return getWrapperAt(i).getComponent();
  }

  private TabWrapper getWrapperAt(final int i) {
    return (TabWrapper)tabbedPane.getComponentAt(i);
  }

  public final void setTitleAt(final int index, @NotNull @TabTitle String title) {
    assertIsDispatchThread();
    tabbedPane.setTitleAt(index, title);
  }

  public final void setToolTipTextAt(final int index, @NlsContexts.Tooltip String toolTipText) {
    assertIsDispatchThread();
    tabbedPane.setToolTipTextAt(index, toolTipText);
  }

  /**
   * @see JTabbedPane#setComponentAt(int, Component)
   */
  public final synchronized void setComponentAt(final int index, final @NotNull JComponent component) {
    assertIsDispatchThread();
    tabbedPane.setComponentAt(index, createTabWrapper(component));
  }

  /**
   * @see JTabbedPane#setIconAt(int, Icon)
   */
  public final void setIconAt(final int index, final Icon icon) {
    assertIsDispatchThread();
    tabbedPane.setIconAt(index, icon);
  }

  public final void setEnabledAt(final int index, final boolean enabled) {
    assertIsDispatchThread();
    tabbedPane.setEnabledAt(index, enabled);
  }

  /**
   * @see JTabbedPane#indexOfComponent(Component)
   */
  public final synchronized int indexOfComponent(final JComponent component) {
    for (int i = 0; i < tabbedPane.getTabCount(); i++) {
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
  public final synchronized int getTabLayoutPolicy() {
    return tabbedPane.getTabLayoutPolicy();
  }

  /**
   * @see JTabbedPane#setTabLayoutPolicy
   */
  public final synchronized void setTabLayoutPolicy(final int policy) {
    tabbedPane.setTabLayoutPolicy(policy);
    final int index = tabbedPane.getSelectedIndex();
    if (index != -1) {
      tabbedPane.scrollTabToVisible(index);
    }
  }

  public final @Nls String getTitleAt(final int i) {
    return tabbedPane.getTitleAt(i);
  }

  public @Nullable String getSelectedTitle() {
    return getSelectedIndex() < 0 ? null : getTitleAt(getSelectedIndex());
  }

  public void setSelectedTitle(final @Nullable String title) {
    if (title == null) return;

    for (int i = 0; i < tabbedPane.getTabCount(); i++) {
      final String each = tabbedPane.getTitleAt(i);
      if (title.equals(each)) {
        tabbedPane.setSelectedIndex(i);
        break;
      }
    }
  }

  public void removeAll() {
    tabbedPane.removeAll();
  }

  public @NotNull TabbedPane getTabbedPane() {
    return tabbedPane;
  }

  public static TabbedPaneWrapper get(JTabbedPane tabs) {
    return (TabbedPaneWrapper)tabs.getClientProperty(TabbedPaneWrapper.class);
  }

  public static @NotNull TabbedPaneWrapper createJbTabs(@Nullable Project project,
                                                        int tabPlacement,
                                                        PrevNextActionsDescriptor installKeyboardNavigation,
                                                        @NotNull Disposable parentDisposable) {
    TabbedPaneWrapper result = new TabbedPaneWrapper(false);
    result.init(tabPlacement, installKeyboardNavigation, new JBTabsFactory(result, project, parentDisposable));
    return result;
  }

  private interface TabFactory {
    @NotNull
    TabbedPane createTabbedPane(int tabPlacement);

    @NotNull
    TabbedPaneHolder createTabbedPaneHolder();

    @NotNull
    TabWrapper createTabWrapper(@NotNull JComponent component);
  }

  public static final class TabWrapper extends JPanel implements UiCompatibleDataProvider {
    boolean myCustomFocus = true;
    private JComponent myComponent;

    TabWrapper(final @NotNull JComponent component) {
      super(new BorderLayout());
      myComponent = component;
      add(component, BorderLayout.CENTER);
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      DataSink.uiDataSnapshot(sink, myComponent);
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
                                                                       IdeFocusManager.getGlobalInstance()
                                                                         .requestFocus(preferredFocusedComponent, true));
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

  public static class TabbedPaneHolder extends JPanel {

    private final TabbedPaneWrapper myWrapper;

    protected TabbedPaneHolder(TabbedPaneWrapper wrapper) {
      super(new BorderLayout());
      myWrapper = wrapper;
    }

    @Override
    public boolean requestDefaultFocus() {
      final JComponent preferredFocusedComponent =
        IdeFocusTraversalPolicy.getPreferredFocusedComponent(myWrapper.tabbedPane.getComponent());
      if (preferredFocusedComponent != null) {
        if (!preferredFocusedComponent.requestFocusInWindow()) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() ->
                                                                       IdeFocusManager.getGlobalInstance()
                                                                         .requestFocus(preferredFocusedComponent, true));
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
        myWrapper.tabbedPane.updateUI();
      }
    }

    public TabbedPaneWrapper getTabbedPaneWrapper() {
      return myWrapper;
    }
  }

  private static final class JTabbedPaneFactory implements TabFactory {
    private final TabbedPaneWrapper myWrapper;

    private JTabbedPaneFactory(TabbedPaneWrapper wrapper) {
      myWrapper = wrapper;
    }

    @Override
    public @NotNull TabbedPane createTabbedPane(int tabPlacement) {
      return new TabbedPaneImpl(tabPlacement);
    }

    @Override
    public @NotNull TabbedPaneHolder createTabbedPaneHolder() {
      return new TabbedPaneHolder(myWrapper);
    }

    @Override
    public @NotNull TabWrapper createTabWrapper(@NotNull JComponent component) {
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

    @Override
    public @NotNull TabbedPane createTabbedPane(int tabPlacement) {
      return new JBTabsPaneImpl(myProject, tabPlacement, myParent);
    }

    @Override
    public @NotNull TabbedPaneHolder createTabbedPaneHolder() {
      return new TabbedPaneHolder(myWrapper) {
        @Override
        public boolean requestDefaultFocus() {
          getTabs().requestFocus();
          return true;
        }
      };
    }

    @Override
    public @NotNull TabWrapper createTabWrapper(@NotNull JComponent component) {
      TabWrapper tabWrapper = new TabWrapper(component);
      tabWrapper.myCustomFocus = false;
      return tabWrapper;
    }

    public JBTabs getTabs() {
      return ((JBTabsPaneImpl)myWrapper.tabbedPane).getTabs();
    }
  }

  /**
   * @deprecated Use {@link #createJbTabs}
   */
  @Deprecated
  public static final class AsJBTabs extends TabbedPaneWrapper {
    public AsJBTabs(@Nullable Project project,
                    int tabPlacement,
                    PrevNextActionsDescriptor installKeyboardNavigation,
                    @NotNull Disposable parent) {
      super(false);

      init(tabPlacement, installKeyboardNavigation, new JBTabsFactory(this, project, parent));
    }

    public @NotNull JBTabs getTabs() {
      return ((JBTabsPaneImpl)tabbedPane).getTabs();
    }
  }

  public static class AsJTabbedPane extends TabbedPaneWrapper {
    public AsJTabbedPane(int tabPlacement) {
      super(false);
      init(tabPlacement, TabbedPaneImpl.DEFAULT_PREV_NEXT_SHORTCUTS, new JTabbedPaneFactory(this));
    }
  }

  private final class _MyFocusTraversalPolicy extends ComponentsListFocusTraversalPolicy {
    @Override
    protected @NotNull List<Component> getOrderedComponents() {
      List<Component> result = new ArrayList<>();
      if (tabbedPane.getSelectedComponent() != null) {
        result.add(tabbedPane.getSelectedComponent());
      }
      return result;
    }
  }
}
