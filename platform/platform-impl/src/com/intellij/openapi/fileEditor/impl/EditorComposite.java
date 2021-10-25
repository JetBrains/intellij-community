// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

/**
 * This class hides internal structure of UI component which represent
 * set of opened editors. For example, one myEditor is represented by its
 * component, more then one myEditor is wrapped into tabbed pane.
 *
 * @author Vladimir Kondratyev
 */
public class EditorComposite implements Disposable {
  private static final Logger LOG = Logger.getInstance(EditorComposite.class);

  /**
   * File for which composite is created
   */
  @NotNull private final VirtualFile myFile;
  /**
   * Whether the composite is pinned or not
   */
  private boolean myPinned;
  /**
   * Whether the composite is opened as preview tab or not
   */
  private boolean myPreview;
  /**
   * Editors which are opened in the composite
   */
  volatile FileEditor[] myEditors;
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
  private FileEditor mySelectedEditor;
  private final FileEditorManagerEx myFileEditorManager;
  private final Map<FileEditor, JComponent> myTopComponents = new HashMap<>();
  private final Map<FileEditor, JComponent> myBottomComponents = new HashMap<>();
  private final Map<FileEditor, @NlsContexts.TabTitle String> myDisplayNames = new HashMap<>();

  private FileEditorProvider[] myProviders;

  /**
   * @param file {@code file} for which composite is being constructed
   *
   * @param editors {@code edittors} that should be placed into the composite
   *
   * @exception IllegalArgumentException if {@code editors}
   * is {@code null} or {@code providers} is {@code null} or {@code myEditor} arrays is empty
   */
  EditorComposite(@NotNull final VirtualFile file,
                  @NotNull FileEditor @NotNull [] editors,
                  @NotNull FileEditorProvider @NotNull [] providers,
                  @NotNull final FileEditorManagerEx fileEditorManager) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myFile = file;
    myEditors = editors;
    myProviders = providers;
    for (FileEditor editor : editors) {
      FileEditor.FILE_KEY.set(editor, myFile);
    }
    if (ArrayUtil.contains(null, editors)) throw new IllegalArgumentException("Must not pass null editors in " + Arrays.asList(editors));
    myFileEditorManager = fileEditorManager;
    myInitialFileTimeStamp = myFile.getTimeStamp();

    Project project = fileEditorManager.getProject();
    Disposer.register(project, this);

    if (editors.length > 1) {
      myTabbedPaneWrapper = createTabbedPaneWrapper(editors, null);
      JComponent component = myTabbedPaneWrapper.getComponent();
      myComponent = new MyComponent(component, () -> component);
    }
    else if (editors.length == 1) {
      myTabbedPaneWrapper = null;
      FileEditor editor = editors[0];
      myComponent = new MyComponent(createEditorComponent(editor), editor::getPreferredFocusedComponent);
    }
    else {
      throw new IllegalArgumentException("editors array cannot be empty");
    }

    mySelectedEditor = editors[0];
    myFocusWatcher = new FocusWatcher();
    myFocusWatcher.install(myComponent);

    project.getMessageBus().connect(this).subscribe(
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

              FileEditorCollector.logAlternativeFileEditorSelected(project, newFile, newEditor);
            }
            ((FileEditorProviderManagerImpl)FileEditorProviderManager.getInstance()).providerSelected(EditorComposite.this);
            ((IdeDocumentHistoryImpl)IdeDocumentHistory.getInstance(myFileEditorManager.getProject())).onSelectionChanged();
          };
          if (ApplicationManager.getApplication().isDispatchThread()) {
            CommandProcessor.getInstance().executeCommand(myFileEditorManager.getProject(), runnable,
                                                          IdeBundle.message("command.switch.active.editor"), null);
          }
          else {
            runnable.run(); // not invoked by user
          }
        }
      }
    });
  }

  public FileEditorProvider @NotNull [] getProviders() {
    return myProviders;
  }

  @NotNull
  private TabbedPaneWrapper.AsJBTabs createTabbedPaneWrapper(FileEditor @NotNull [] editors, MyComponent myComponent) {
    PrevNextActionsDescriptor descriptor = new PrevNextActionsDescriptor(IdeActions.ACTION_NEXT_EDITOR_TAB, IdeActions.ACTION_PREVIOUS_EDITOR_TAB);
    final TabbedPaneWrapper.AsJBTabs wrapper = new TabbedPaneWrapper.AsJBTabs(myFileEditorManager.getProject(), SwingConstants.BOTTOM, descriptor, this);

    boolean firstEditor = true;
    for (FileEditor editor : editors) {
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
      comp = DumbService.getInstance(myFileEditorManager.getProject()).wrapGently(comp, editor);
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
    myPinned = pinned;
    Container parent = getComponent().getParent();
    if (parent instanceof JComponent) {
      ((JComponent)parent).putClientProperty(JBTabsImpl.PINNED, myPinned ? Boolean.TRUE : null);
    }
  }

  public boolean isPreview() {
    return myPreview;
  }

  void setPreview(final boolean preview) {
    myPreview = preview;
  }

  private void fireSelectedEditorChanged(@NotNull FileEditor oldSelectedEditor, @NotNull FileEditor newSelectedEditor) {
    if ((!EventQueue.isDispatchThread() || !myFileEditorManager.isInsideChange()) && !Comparing.equal(oldSelectedEditor, newSelectedEditor)) {
      myFileEditorManager.notifyPublisher(() -> {
        final FileEditorManagerEvent event = new FileEditorManagerEvent(myFileEditorManager, myFile, oldSelectedEditor, myFile, newSelectedEditor);
        final FileEditorManagerListener publisher = myFileEditorManager.getProject().getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER);
        publisher.selectionChanged(event);
      });
      final JComponent component = newSelectedEditor.getComponent();
      final EditorWindowHolder holder =
        ComponentUtil.getParentOfType((Class<? extends EditorWindowHolder>)EditorWindowHolder.class, (Component)component);
      if (holder != null) {
        ((FileEditorManagerImpl)myFileEditorManager).addSelectionRecord(myFile, holder.getEditorWindow());
      }
    }
  }


  /**
   * @return preferred focused component inside myEditor composite. Composite uses FocusWatcher to
   * track focus movement inside the myEditor.
   */
  @Nullable
  public JComponent getPreferredFocusedComponent(){
    if (mySelectedEditor == null) return null;

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
   */
  public FileEditor @NotNull [] getEditors() {
    return myEditors;
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
    }
    else {
      NonOpaquePanel wrapper = new NonOpaquePanel(component);
      if (!Boolean.TRUE.equals(component.getClientProperty(FileEditorManager.SEPARATOR_DISABLED))) {
        wrapper.setBorder(createTopBottomSideBorder(top));
      }
      container.add(wrapper, calcComponentInsertionIndex(component, container));
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
    int index = ContainerUtil.indexOfIdentity(ContainerUtil.immutableList(myEditors), editor);
    assert index != -1;

    myDisplayNames.put(editor, name);
    if (myTabbedPaneWrapper != null) {
      myTabbedPaneWrapper.setTitleAt(index, name);
    }
  }

  @NotNull
  protected @NlsContexts.TabTitle String getDisplayName(@NotNull FileEditor editor) {
    return ObjectUtils.notNull(myDisplayNames.get(editor), editor.getName());
  }

  /**
   * @return currently selected myEditor.
   */
  @NotNull
  FileEditor getSelectedEditor() {
    return getSelectedWithProvider().getFileEditor();
  }

  /**
   * @return currently selected myEditor with its provider.
   */
  @NotNull
  public FileEditorWithProvider getSelectedWithProvider() {
    LOG.assertTrue(myEditors.length > 0, myEditors.length);
    if (myEditors.length == 1) {
      LOG.assertTrue(myTabbedPaneWrapper == null);
      return new FileEditorWithProvider(myEditors[0], myProviders[0]);
    }
    else {
      // we have to get myEditor from tabbed pane
      LOG.assertTrue(myTabbedPaneWrapper != null);
      int index = myTabbedPaneWrapper.getSelectedIndex();
      if (index == -1) {
        index = 0;
      }
      LOG.assertTrue(index >= 0, index);
      LOG.assertTrue(index < myEditors.length, index);
      return new FileEditorWithProvider(myEditors[index], myProviders[index]);
    }
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

  void setSelectedEditor(final int index) {
    if (myEditors.length == 1) {
      // nothing to do
      LOG.assertTrue(myTabbedPaneWrapper == null);
    }
    else {
      LOG.assertTrue(myTabbedPaneWrapper != null);
      myTabbedPaneWrapper.setSelectedIndex(index);
    }
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
    return ContainerUtil.exists(getEditors(), editor -> editor.isModified());
  }

  /**
   * Handles changes of selected myEditor
   */
  private final class MyChangeListener implements ChangeListener{
    @Override
    public void stateChanged(ChangeEvent e) {
      FileEditor oldSelectedEditor = mySelectedEditor;
      LOG.assertTrue(oldSelectedEditor != null);
      int selectedIndex = myTabbedPaneWrapper.getSelectedIndex();
      LOG.assertTrue(selectedIndex != -1);
      mySelectedEditor = myEditors[selectedIndex];
      fireSelectedEditorChanged(oldSelectedEditor, mySelectedEditor);
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
    for (FileEditor editor : myEditors) {
      if (!Disposer.isDisposed(editor)) {
        Disposer.dispose(editor);
      }
    }
    myFocusWatcher.deinstall(myFocusWatcher.getTopComponent());
  }

  private void addEditor(@NotNull FileEditor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    //noinspection NonAtomicOperationOnVolatileField : field is modified only in EDT
    myEditors = ArrayUtil.append(myEditors, editor);
    FileEditor.FILE_KEY.set(editor, myFile);
    if (myTabbedPaneWrapper == null) {
      myTabbedPaneWrapper = createTabbedPaneWrapper(myEditors, myComponent);
      myComponent.setComponent(myTabbedPaneWrapper.getComponent());
    }
    else {
      JComponent component = createEditorComponent(editor);
      myTabbedPaneWrapper.addTab(getDisplayName(editor), component);
    }
    myFocusWatcher.deinstall(myFocusWatcher.getTopComponent());
    myFocusWatcher.install(myComponent);
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

  public void addEditor(@NotNull FileEditor editor, @NotNull FileEditorProvider provider) {
    addEditor(editor);
    myProviders = ArrayUtil.append(myProviders, provider);
  }
}