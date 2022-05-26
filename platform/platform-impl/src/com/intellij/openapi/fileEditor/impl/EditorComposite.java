// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.featureStatistics.fusCollectors.FileEditorCollector;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * An abstraction over one or several file editors opened in the same tab (e.g. designer and code-behind).
 * It's a composite what can be pinned in the tabs list or opened as a preview, not concrete file editors.
 * It also manages the internal UI structure: bottom and top components, panels, labels, actions for navigating between editors it owns.
 */
public class EditorComposite extends UserDataHolderBase implements Disposable {
  private static final Logger LOG = Logger.getInstance(EditorComposite.class);

  /**
   * File for which composite is created
   */
  @NotNull private final VirtualFile myFile;

  @NotNull private final Project myProject;
  /**
   * Whether the composite is pinned or not
   */
  private boolean myPinned;
  /**
   * Whether the composite is opened as preview tab or not
   */
  private boolean myPreview;
  /**
   * This is initial timestamp of the file. It uses to implement
   * "close non modified editors first" feature.
   */
  private final long myInitialFileTimeStamp;
  private TabbedPaneWrapper myTabbedPaneWrapper;
  @NotNull
  private final MyComponent myComponent;
  private final FocusWatcher myFocusWatcher;
  /**
   * Currently selected myEditor
   */
  private FileEditorWithProvider mySelectedEditorWithProvider;
  private final FileEditorManagerEx myFileEditorManager;
  private final Map<FileEditor, JComponent> myTopComponents = new HashMap<>();
  private final Map<FileEditor, JComponent> myBottomComponents = new HashMap<>();
  private final Map<FileEditor, @NlsContexts.TabTitle String> myDisplayNames = new HashMap<>();

  /**
   * Editors opened in the composite
   */
  private final List<FileEditorWithProvider> myEditorWithProviders = new CopyOnWriteArrayList<>();

  private final EventDispatcher<EditorCompositeListener> myDispatcher = EventDispatcher.create(EditorCompositeListener.class);

  EditorComposite(@NotNull final VirtualFile file,
                  @NotNull List<@NotNull FileEditorWithProvider> editorWithProviders,
                  @NotNull final FileEditorManagerEx fileEditorManager) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myFile = file;
    myEditorWithProviders.addAll(editorWithProviders);
    for (FileEditorWithProvider editorWithProvider : myEditorWithProviders) {
      FileEditor.FILE_KEY.set(editorWithProvider.getFileEditor(), myFile);
    }
    myFileEditorManager = fileEditorManager;
    myInitialFileTimeStamp = myFile.getTimeStamp();

    myProject = fileEditorManager.getProject();
    Disposer.register(myProject, this);

    if (myEditorWithProviders.size() > 1) {
      myTabbedPaneWrapper = createTabbedPaneWrapper(null);
      JComponent component = myTabbedPaneWrapper.getComponent();
      myComponent = new MyComponent(component, () -> component);
    }
    else if (myEditorWithProviders.size() == 1) {
      myTabbedPaneWrapper = null;
      FileEditor editor = myEditorWithProviders.get(0).getFileEditor();
      myComponent = new MyComponent(createEditorComponent(editor), editor::getPreferredFocusedComponent);
    }
    else {
      throw new IllegalArgumentException("editors array cannot be empty");
    }

    mySelectedEditorWithProvider = myEditorWithProviders.get(0);
    myFocusWatcher = new FocusWatcher();
    myFocusWatcher.install(myComponent);

    myProject.getMessageBus().connect(this).subscribe(
          FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull final FileEditorManagerEvent event) {
        final VirtualFile oldFile = event.getOldFile();
        final VirtualFile newFile = event.getNewFile();
        if (Comparing.equal(oldFile, newFile) && Comparing.equal(getFile(), newFile)) {
          Runnable runnable = () -> {
            final FileEditor oldEditor = event.getOldEditor();
            if (oldEditor != null) oldEditor.deselectNotify();
            final FileEditor newEditor = event.getNewEditor();
            if (newEditor != null) {
              newEditor.selectNotify();

              FileEditorCollector.logAlternativeFileEditorSelected(myProject, newFile, newEditor);
            }
            ((FileEditorProviderManagerImpl)FileEditorProviderManager.getInstance()).providerSelected(EditorComposite.this);
            ((IdeDocumentHistoryImpl)IdeDocumentHistory.getInstance(myProject)).onSelectionChanged();
          };
          if (ApplicationManager.getApplication().isDispatchThread()) {
            CommandProcessor.getInstance().executeCommand(myProject, runnable,
                                                          IdeBundle.message("command.switch.active.editor"), null);
          }
          else {
            runnable.run(); // not invoked by user
          }
        }
      }
    });
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  /**
   * @deprecated use {@link #getAllEditorsWithProviders()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2023.1")
  public FileEditorProvider @NotNull [] getProviders() {
    return getAllProviders().toArray(FileEditorProvider.EMPTY_ARRAY);
  }

  public @NotNull List<@NotNull FileEditorProvider> getAllProviders() {
    return ContainerUtil.map(getAllEditorsWithProviders(), it -> it.getProvider());
  }

  @NotNull
  private TabbedPaneWrapper.AsJBTabs createTabbedPaneWrapper(MyComponent myComponent) {
    PrevNextActionsDescriptor descriptor = new PrevNextActionsDescriptor(IdeActions.ACTION_NEXT_EDITOR_TAB, IdeActions.ACTION_PREVIOUS_EDITOR_TAB);
    final TabbedPaneWrapper.AsJBTabs wrapper = new TabbedPaneWrapper.AsJBTabs(myProject, SwingConstants.BOTTOM, descriptor, this);

    boolean firstEditor = true;
    for (FileEditorWithProvider editorWithProvider : myEditorWithProviders) {
      FileEditor editor = editorWithProvider.getFileEditor();
      JComponent component = firstEditor && myComponent != null ? (JComponent)myComponent.getComponent(0) : createEditorComponent(editor);
      wrapper.addTab(getDisplayName(editor), component);
      firstEditor = false;
    }
    wrapper.addChangeListener(new MyChangeListener());

    return wrapper;
  }

  @NotNull
  private JComponent createEditorComponent(@NotNull FileEditor editor) {
    JPanel component = new JPanel(new BorderLayout());
    JComponent comp = editor.getComponent();
    if (!FileEditorManagerImpl.isDumbAware(editor)) {
      comp = DumbService.getInstance(myProject).wrapGently(comp, editor);
    }

    component.add(comp, BorderLayout.CENTER);

    JPanel topPanel = new TopBottomPanel();
    myTopComponents.put(editor, topPanel);
    component.add(topPanel, BorderLayout.NORTH);

    final JPanel bottomPanel = new TopBottomPanel();
    myBottomComponents.put(editor, bottomPanel);
    component.add(bottomPanel, BorderLayout.SOUTH);

    return component;
  }

  /**
   * @return whether myEditor composite is pinned
   */
  public boolean isPinned(){
    return myPinned;
  }

  /**
   * Sets new "pinned" state
   */
  void setPinned(final boolean pinned) {
    if (pinned == myPinned) return;
    myPinned = pinned;
    Container parent = getComponent().getParent();
    if (parent instanceof JComponent) {
      ((JComponent)parent).putClientProperty(JBTabsImpl.PINNED, myPinned ? Boolean.TRUE : null);
    }
    myDispatcher.getMulticaster().isPinnedChanged(pinned);
  }

  public boolean isPreview() {
    return myPreview;
  }

  void setPreview(final boolean preview) {
    if (preview == myPreview) return;
    myPreview = preview;
    myDispatcher.getMulticaster().isPreviewChanged(preview);
  }

  protected void fireSelectedEditorChanged(@NotNull FileEditorWithProvider oldEditorWithProvider, @NotNull FileEditorWithProvider newEditorWithProvider) {
    if (EventQueue.isDispatchThread() && myFileEditorManager.isInsideChange() ||
        Comparing.equal(oldEditorWithProvider, newEditorWithProvider)) {
      return;
    }

    myFileEditorManager.notifyPublisher(() -> {
      final FileEditorManagerEvent event = new FileEditorManagerEvent(myFileEditorManager, oldEditorWithProvider, newEditorWithProvider);
      final FileEditorManagerListener publisher =
        myProject.getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER);
      publisher.selectionChanged(event);
    });
    final JComponent component = newEditorWithProvider.getFileEditor().getComponent();
    final EditorWindowHolder holder =
      ComponentUtil.getParentOfType((Class<? extends EditorWindowHolder>)EditorWindowHolder.class, (Component)component);
    if (holder != null) {
      ((FileEditorManagerImpl)myFileEditorManager).addSelectionRecord(myFile, holder.getEditorWindow());
    }
  }

  public void addListener(EditorCompositeListener listener, Disposable disposable) {
    myDispatcher.addListener(listener, disposable);
  }

  /**
   * @return preferred focused component inside myEditor composite. Composite uses FocusWatcher to
   * track focus movement inside the myEditor.
   */
  @Nullable
  public JComponent getPreferredFocusedComponent() {
    if (mySelectedEditorWithProvider == null) return null;

    final Component component = myFocusWatcher.getFocusedComponent();
    if (!(component instanceof JComponent) || !component.isShowing() || !component.isEnabled() || !component.isFocusable()) {
      return getSelectedEditor().getPreferredFocusedComponent();
    }
    return (JComponent)component;
  }

  /**
   * @return file for which composite was created.
   */
  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  public FileEditorManager getFileEditorManager() {
    return myFileEditorManager;
  }

  /**
   * @return initial time stamp of the file (on moment of creation of
   * the composite)
   */
  public long getInitialFileTimeStamp() {
    return myInitialFileTimeStamp;
  }

  /**
   * @return editors which are opened in the composite. <b>Do not modify
   * this array</b>.
   * @deprecated use {@link #getAllEditors()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2023.1")
  public FileEditor @NotNull [] getEditors() {
    return getAllEditors().toArray(FileEditor.EMPTY_ARRAY);
  }

  public @NotNull List<@NotNull FileEditor> getAllEditors() {
    return ContainerUtil.map(getAllEditorsWithProviders(), it -> it.getFileEditor());
  }

  public @NotNull List<@NotNull FileEditorWithProvider> getAllEditorsWithProviders() {
    return Collections.unmodifiableList(new SmartList<>(myEditorWithProviders));
  }

  @NotNull
  List<JComponent> getTopComponents(@NotNull FileEditor editor) {
    List<JComponent> result = new SmartList<>();
    JComponent container = myTopComponents.get(editor);
    for (Component each : container.getComponents()) {
      if (each instanceof NonOpaquePanel) {
        result.add(((NonOpaquePanel)each).getTargetComponent());
      }
    }
    return Collections.unmodifiableList(result);
  }

  @Nullable
  public JBTabs getTabs() {
    return myTabbedPaneWrapper == null ? null : ((TabbedPaneWrapper.AsJBTabs)myTabbedPaneWrapper).getTabs();
  }

  public void addTopComponent(@NotNull FileEditor editor, @NotNull JComponent component) {
    manageTopOrBottomComponent(editor, component, true, false);
  }

  public void removeTopComponent(@NotNull FileEditor editor, @NotNull JComponent component) {
    manageTopOrBottomComponent(editor, component, true, true);
  }

  public void addBottomComponent(@NotNull FileEditor editor, @NotNull JComponent component) {
    manageTopOrBottomComponent(editor, component, false, false);
  }

  public void removeBottomComponent(@NotNull FileEditor editor, @NotNull JComponent component) {
    manageTopOrBottomComponent(editor, component, false, true);
  }

  private void manageTopOrBottomComponent(@NotNull FileEditor editor, @NotNull JComponent component, boolean top, boolean remove) {
    final JComponent container = top ? myTopComponents.get(editor) : myBottomComponents.get(editor);
    assert container != null;

    if (remove) {
      container.remove(component.getParent());
      EditorCompositeListener multicaster = myDispatcher.getMulticaster();
      if (top) {
        multicaster.topComponentRemoved(editor, component);
      }
      else {
        multicaster.bottomComponentRemoved(editor,component);
      }
    }
    else {
      NonOpaquePanel wrapper = new NonOpaquePanel(component);
      if (!Boolean.TRUE.equals(component.getClientProperty(FileEditorManager.SEPARATOR_DISABLED))) {
        wrapper.setBorder(createTopBottomSideBorder(top));
      }
      int index = calcComponentInsertionIndex(component, container);
      container.add(wrapper, index);
      EditorCompositeListener multicaster = myDispatcher.getMulticaster();
      if (top) {
        multicaster.topComponentAdded(editor, index, component);
      }
      else {
        multicaster.bottomComponentAdded(editor, index, component);
      }
    }
    container.revalidate();
  }

  private static int calcComponentInsertionIndex(@NotNull JComponent newComponent, @NotNull JComponent container) {
    for (int i = 0, max = container.getComponentCount(); i < max; i++) {
      Component childWrapper = container.getComponent(i);
      Component childComponent = childWrapper instanceof Wrapper ? ((Wrapper)childWrapper).getTargetComponent() : childWrapper;
      boolean weighted1 = newComponent instanceof Weighted;
      boolean weighted2 = childComponent instanceof Weighted;
      if (!weighted2) continue;
      if (!weighted1) return i;

      double w1 = ((Weighted)newComponent).getWeight();
      double w2 = ((Weighted)childComponent).getWeight();
      if (w1 < w2) return i;
    }
    return -1;
  }

  public void setDisplayName(@NotNull FileEditor editor, @NlsContexts.TabTitle @NotNull String name) {
    int index = ContainerUtil.indexOf(myEditorWithProviders, it -> it.getFileEditor().equals(editor));
    assert index != -1;

    myDisplayNames.put(editor, name);
    if (myTabbedPaneWrapper != null) {
      myTabbedPaneWrapper.setTitleAt(index, name);
    }
    myDispatcher.getMulticaster().displayNameChanged(editor, name);
  }

  @NotNull
  protected @NlsContexts.TabTitle String getDisplayName(@NotNull FileEditor editor) {
    return ObjectUtils.notNull(myDisplayNames.get(editor), editor.getName());
  }

  /**
   * @return currently selected myEditor.
   */
  @NotNull
  public FileEditor getSelectedEditor() {
    return getSelectedWithProvider().getFileEditor();
  }

  /**
   * @return currently selected myEditor with its provider.
   */
  @NotNull
  public FileEditorWithProvider getSelectedWithProvider() {
    if (myEditorWithProviders.size() == 1) {
      LOG.assertTrue(myTabbedPaneWrapper == null);
      return myEditorWithProviders.get(0);
    }
    else {
      // we have to get myEditor from tabbed pane
      LOG.assertTrue(myTabbedPaneWrapper != null);
      int index = myTabbedPaneWrapper.getSelectedIndex();
      if (index == -1) {
        index = 0;
      }
      LOG.assertTrue(index >= 0, index);
      LOG.assertTrue(index < myEditorWithProviders.size(), index);
      return myEditorWithProviders.get(index);
    }
  }

  public void setSelectedEditor(@NotNull String providerId) {
    FileEditorWithProvider newSelection = ContainerUtil.find(myEditorWithProviders, it -> it.getProvider().getEditorTypeId().equals(providerId));
    LOG.assertTrue(newSelection != null, "Unable to find providerId=" + providerId);
    setSelectedEditor(newSelection);
  }

  public void setSelectedEditor(@NotNull FileEditor editor) {
    FileEditorWithProvider newSelection = ContainerUtil.find(myEditorWithProviders, it -> it.getFileEditor().equals(editor));
    LOG.assertTrue(newSelection != null, "Unable to find editor=" + editor);
    setSelectedEditor(newSelection);
  }

  public void setSelectedEditor(@NotNull FileEditorWithProvider editorWithProvider) {
    if (myEditorWithProviders.size() == 1) {
      LOG.assertTrue(myTabbedPaneWrapper == null);
      LOG.assertTrue(editorWithProvider.equals(myEditorWithProviders.get(0)));
      return;
    }

    int index = myEditorWithProviders.indexOf(editorWithProvider);
    LOG.assertTrue(index != -1);
    LOG.assertTrue(myTabbedPaneWrapper != null);
    myTabbedPaneWrapper.setSelectedIndex(index);
  }

  /**
   * @deprecated use {@link #getSelectedWithProvider()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @NotNull
  public Pair<FileEditor, FileEditorProvider> getSelectedEditorWithProvider() {
    FileEditorWithProvider info = getSelectedWithProvider();
    return Pair.create(info.getFileEditor(), info.getProvider());
  }

  /**
   * @return component which represents set of file editors in the UI
   */
  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  /**
   * @return component which represents the component that is supposed to be focusable
   */
  @Nullable
  public JComponent getFocusComponent() {
    return myComponent.myFocusComponent.get();
  }

  /**
   * @return {@code true} if the composite contains at least one modified myEditor
   */
  public boolean isModified() {
    return ContainerUtil.exists(getAllEditors(), editor -> editor.isModified());
  }

  /**
   * Handles changes of selected myEditor
   */
  private final class MyChangeListener implements ChangeListener{
    @Override
    public void stateChanged(ChangeEvent e) {
      FileEditorWithProvider oldSelectedEditorWithProvider = mySelectedEditorWithProvider;
      int selectedIndex = myTabbedPaneWrapper.getSelectedIndex();
      LOG.assertTrue(selectedIndex != -1);
      mySelectedEditorWithProvider = myEditorWithProviders.get(selectedIndex);
      fireSelectedEditorChanged(oldSelectedEditorWithProvider, mySelectedEditorWithProvider);
    }
  }

  public static boolean isEditorComposite(@NotNull Component component) {
    return component instanceof MyComponent;
  }

  private class MyComponent extends JPanel implements DataProvider{
    private @NotNull Supplier<? extends JComponent> myFocusComponent;

    MyComponent(@NotNull JComponent realComponent, @NotNull Supplier<? extends JComponent> focusComponent) {
      super(new BorderLayout());
      setFocusable(false);
      myFocusComponent = focusComponent;
      add(realComponent, BorderLayout.CENTER);
    }

    void setComponent(JComponent newComponent) {
      add(newComponent, BorderLayout.CENTER);
      myFocusComponent = () -> newComponent;
    }

    @Override
    public boolean requestFocusInWindow() {
      JComponent focusComponent = myFocusComponent.get();
      return focusComponent != null && focusComponent.requestFocusInWindow();
    }

    @Override
    public void requestFocus() {
      JComponent focusComponent = myFocusComponent.get();
      if (focusComponent != null) {
        IdeFocusManager.getGlobalInstance()
          .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(focusComponent, true));
      }
    }

    @Override
    public boolean requestDefaultFocus() {
      JComponent focusComponent = myFocusComponent.get();
      return focusComponent != null && focusComponent.requestDefaultFocus();
    }

    @Override
    public final Object getData(@NotNull String dataId) {
      if (PlatformCoreDataKeys.FILE_EDITOR.is(dataId)) {
        return getSelectedEditor();
      }
      if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
        return myFile.isValid() ? myFile : null;
      }
      if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
        return myFile.isValid() ? new VirtualFile[]{myFile} : null;
      }
      JComponent component = getPreferredFocusedComponent();
      if (component instanceof DataProvider && component != this) {
        return ((DataProvider)component).getData(dataId);
      }
      return null;
    }
  }

  @Override
  public void dispose() {
    for (FileEditorWithProvider editor : myEditorWithProviders) {
      if (!Disposer.isDisposed(editor.getFileEditor())) {
        Disposer.dispose(editor.getFileEditor());
      }
    }
    myFocusWatcher.deinstall(myFocusWatcher.getTopComponent());
  }

  public void addEditor(@NotNull FileEditor editor, @NotNull FileEditorProvider provider) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    FileEditorWithProvider editorWithProvider = new FileEditorWithProvider(editor, provider);
    myEditorWithProviders.add(editorWithProvider);

    FileEditor.FILE_KEY.set(editor, myFile);
    if (myTabbedPaneWrapper == null) {
      myTabbedPaneWrapper = createTabbedPaneWrapper(myComponent);
      myComponent.setComponent(myTabbedPaneWrapper.getComponent());
    }
    else {
      JComponent component = createEditorComponent(editor);
      myTabbedPaneWrapper.addTab(getDisplayName(editor), component);
    }
    myFocusWatcher.deinstall(myFocusWatcher.getTopComponent());
    myFocusWatcher.install(myComponent);
    myDispatcher.getMulticaster().editorAdded(editorWithProvider);
  }

  private static final class TopBottomPanel extends JBPanelWithEmptyText {
    private TopBottomPanel() {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    @Override
    public Color getBackground() {
      EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
      if (ExperimentalUI.isNewEditorTabs()) {
        return globalScheme.getDefaultBackground();
      }
      Color color = globalScheme.getColor(EditorColors.GUTTER_BACKGROUND);
      return color == null ? EditorColors.GUTTER_BACKGROUND.getDefaultColor() : color;
    }
  }

  @NotNull
  private static SideBorder createTopBottomSideBorder(boolean top) {
    return new SideBorder(null, top ? SideBorder.BOTTOM : SideBorder.TOP) {
      @Override
      public Color getLineColor() {
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        if (ExperimentalUI.isNewEditorTabs()) {
          return scheme.getDefaultBackground();
        }
        Color result = scheme.getColor(top ? EditorColors.SEPARATOR_ABOVE_COLOR : EditorColors.SEPARATOR_BELOW_COLOR);
        if (result == null) result = scheme.getColor(EditorColors.TEARLINE_COLOR);
        return result == null ? JBColor.BLACK : result;
      }
    };
  }

  @NotNull HistoryEntry currentStateAsHistoryEntry() {
    final FileEditor[] editors = getEditors();
    final FileEditorState[] states = new FileEditorState[editors.length];
    for (int j = 0; j < states.length; j++) {
      states[j] = editors[j].getState(FileEditorStateLevel.FULL);
      LOG.assertTrue(states[j] != null);
    }
    final int selectedProviderIndex = ArrayUtil.find(editors, getSelectedEditor());
    LOG.assertTrue(selectedProviderIndex != -1);
    final FileEditorProvider[] providers = getProviders();
    return HistoryEntry.createLight(getFile(), providers, states, providers[selectedProviderIndex]);
  }

  /**
   * A mapper for old API with arrays and pairs
   */
  @NotNull
  public static Pair<FileEditor @NotNull [], FileEditorProvider @NotNull []> retrofit(@Nullable EditorComposite composite) {
    if (composite == null) return new Pair<>(FileEditor.EMPTY_ARRAY, FileEditorProvider.EMPTY_ARRAY);

    FileEditor[] editors = composite.getAllEditors().toArray(FileEditor.EMPTY_ARRAY);
    FileEditorProvider[] providers = composite.getAllProviders().toArray(FileEditorProvider.EMPTY_ARRAY);
    return new Pair<>(editors, providers);
  }
}