// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ToggleDistractionFreeModeAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.tabs.TabsUtil;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutInfo;
import com.intellij.util.IconUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

public final class EditorWindow {
  private static final Logger LOG = Logger.getInstance(EditorWindow.class);

  public static final DataKey<EditorWindow> DATA_KEY = DataKey.create("editorWindow");
  public static final Key<Boolean> HIDE_TABS = Key.create("HIDE_TABS");

  JPanel myPanel;
  private final @NotNull EditorTabbedContainer myTabbedPane;
  @NotNull
  private final EditorsSplitters myOwner;

  private boolean myIsDisposed;
  /**
   * @deprecated Use file opening methods taking {@link FileEditorOpenOptions} instead
   * and pass the index through {@link FileEditorOpenOptions#withIndex(int)}.
   */
  @Deprecated
  public static final Key<Integer> INITIAL_INDEX_KEY = Key.create("initial editor index");
  // Metadata to support editor tab drag&drop process: initial index
  public static final Key<Integer> DRAG_START_INDEX_KEY = KeyWithDefaultValue.create("drag start editor index", -1);
  // Metadata to support editor tab drag&drop process: hash of source container
  public static final Key<Integer> DRAG_START_LOCATION_HASH_KEY = KeyWithDefaultValue.create("drag start editor location hash", 0);
  // Metadata to support editor tab drag&drop process: initial 'pinned' state
  public static final Key<Boolean> DRAG_START_PINNED_KEY = Key.create("drag start editor pinned state");
  private final Stack<Pair<String, FileEditorOpenOptions>> myRemovedTabs = new Stack<>() {
    @Override
    public void push(Pair<String, FileEditorOpenOptions> pair) {
      if (size() >= getTabLimit()) {
        remove(0);
      }
      super.push(pair);
    }
  };

  EditorWindow(@NotNull EditorsSplitters owner, @NotNull Disposable parentDisposable) {
    myOwner = owner;
    myPanel = new JPanel(new BorderLayout());
    myPanel.setOpaque(false);
    myPanel.setFocusable(false);

    myTabbedPane = new EditorTabbedContainer(this, getManager().getProject(), parentDisposable);
    myPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);

    // tab layout policy
    if (UISettings.getInstance().getScrollTabLayoutInEditor()) {
      setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    }
    else {
      setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
    }

    myOwner.addWindow(this);
    if (myOwner.getCurrentWindow() == null) {
      myOwner.setCurrentWindow(this, false);
    }
    updateTabsVisibility();
  }

  void updateTabsVisibility() {
    updateTabsVisibility(UISettings.getInstance());
  }

  void updateTabsVisibility(@NotNull UISettings settings) {
    myTabbedPane.getTabs().getPresentation()
      .setHideTabs(myOwner.isFloating() && shouldHideTabs(getSelectedComposite())
                   || settings.getEditorTabPlacement() == UISettings.TABS_NONE
                   || settings.getPresentationMode());
  }

  private static boolean shouldHideTabs(@Nullable EditorComposite composite) {
    return composite != null && ContainerUtil.exists(composite.getAllEditors(), e -> HIDE_TABS.get(e, false));
  }

  public boolean isShowing() {
    return myPanel.isShowing();
  }

  public void closeAllExcept(@Nullable VirtualFile selectedFile) {
    FileEditorManagerImpl.runBulkTabChange(myOwner, __ -> {
      for (VirtualFile file : getFiles()) {
        if (!Comparing.equal(file, selectedFile) && !isFilePinned(file)) {
          closeFile(file);
        }
      }
    });
  }

  void dispose() {
    try {
      myOwner.removeWindow(this);
    }
    finally {
      myIsDisposed = true;
    }
  }

  public boolean isDisposed() {
    return myIsDisposed;
  }

  public void closeFile(@NotNull VirtualFile file) {
    closeFile(file, true);
  }

  public void closeFile(@NotNull VirtualFile file, boolean disposeIfNeeded) {
    closeFile(file, disposeIfNeeded, true);
  }

  boolean hasClosedTabs() {
    return !myRemovedTabs.empty();
  }

  void restoreClosedTab() {
    assert hasClosedTabs() : "Nothing to restore";

    Pair<String, FileEditorOpenOptions> info = myRemovedTabs.pop();
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(info.getFirst());
    if (file != null) {
      getManager().openFileImpl4(this, file, null,
                                 info.getSecond().clone()
                                   .withSelectAsCurrent()
                                   .withRequestFocus());
    }
  }

  public void closeFile(@NotNull VirtualFile file, boolean disposeIfNeeded, boolean transferFocus) {
    FileEditorManagerImpl editorManager = getManager();
    FileEditorManagerImpl.runBulkTabChange(myOwner, splitters -> {
      List<EditorComposite> composites = splitters.getAllComposites(file);
      if (!isDisposed() && composites.isEmpty()) return;
      try {
        EditorComposite composite = getComposite(file);

        FileEditorManagerListener.Before beforePublisher =
          editorManager.getProject().getMessageBus().syncPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER);

        beforePublisher.beforeFileClosed(editorManager, file);

        if (composite != null) {
          int componentIndex = findComponentIndex(composite.getComponent());
          if (componentIndex >= 0) { // composite could close itself on decomposition
            int indexToSelect = calcIndexToSelect(file, componentIndex);
            FileEditorOpenOptions options = new FileEditorOpenOptions().withIndex(componentIndex).withPin(composite.isPinned());
            Pair<String, FileEditorOpenOptions> pair = Pair.create(file.getUrl(), options);
            myRemovedTabs.push(pair);
            myTabbedPane.removeTabAt(componentIndex, indexToSelect, transferFocus);
            editorManager.disposeComposite(composite);
          }
        }
        else {

          if (inSplitter()) {
            Splitter splitter = (Splitter)myPanel.getParent();
            JComponent otherComponent = splitter.getOtherComponent(myPanel);

            if (otherComponent != null) {
              IdeFocusManager.findInstance().requestFocus(otherComponent, true);
            }
          }

          myPanel.removeAll ();
        }

        if (disposeIfNeeded && getTabCount() == 0) {
          removeFromSplitter();
        }
        else {
          myPanel.revalidate();
        }
      }
      finally {
        editorManager.removeSelectionRecord(file, this);

        ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();

        editorManager.notifyPublisher(() -> {
          Project project = editorManager.getProject();
          if (!project.isDisposed()) {
            FileEditorManagerListener afterPublisher =
              project.getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER);
            afterPublisher.fileClosed(editorManager, file);
          }
        });

        splitters.afterFileClosed(file);
      }
    });
  }

  void removeFromSplitter() {
    if (!inSplitter()) return;

    if (myOwner.getCurrentWindow() == this) {
      EditorWindow[] siblings = findSiblings();
      myOwner.setCurrentWindow(siblings[0], true);
    }

    Splitter splitter = (Splitter)myPanel.getParent();
    JComponent otherComponent = splitter.getOtherComponent(myPanel);

    Container parent = splitter.getParent().getParent();
    if (parent instanceof Splitter) {
      Splitter parentSplitter = (Splitter)parent;
      if (parentSplitter.getFirstComponent() == splitter.getParent()) {
        parentSplitter.setFirstComponent(otherComponent);
      }
      else {
        parentSplitter.setSecondComponent(otherComponent);
      }

      normalizeProportionsIfNeed(myOwner.getCurrentWindow().myPanel);
    }
    else if (parent instanceof EditorsSplitters) {
      Component currentFocusComponent = getGlobalInstance().getFocusedDescendantFor(parent);

      parent.removeAll();
      parent.add(otherComponent, BorderLayout.CENTER);
      parent.revalidate();

      if (currentFocusComponent != null) currentFocusComponent.requestFocusInWindow();
    }
    else {
      throw new IllegalStateException("Unknown container: " + parent);
    }

    dispose();
  }

  int calcIndexToSelect(@NotNull VirtualFile fileBeingClosed, int fileIndex) {
    int currentlySelectedIndex = myTabbedPane.getSelectedIndex();
    if (currentlySelectedIndex != fileIndex) {
      // if the file being closed is not currently selected, keep the currently selected file open
      return currentlySelectedIndex;
    }
    UISettings uiSettings = UISettings.getInstance();
    if (uiSettings.getState().getActiveMruEditorOnClose()) {
      // try to open last visited file
      List<VirtualFile> histFiles = EditorHistoryManager.getInstance(getManager ().getProject()).getFileList();
      for (int idx = histFiles.size() - 1; idx >= 0; idx--) {
        VirtualFile histFile = histFiles.get(idx);
        if (histFile.equals(fileBeingClosed)) {
          continue;
        }
        EditorComposite composite = getComposite(histFile);
        if (composite == null) {
          continue; // ????
        }
        int histFileIndex = findComponentIndex(composite.getComponent());
        if (histFileIndex >= 0) {
          // if the file being closed is located before the hist file, then after closing the index of the histFile will be shifted by -1
          return histFileIndex;
        }
      }
    } else
    if (uiSettings.getActiveRightEditorOnClose() && fileIndex + 1 < myTabbedPane.getTabCount()) {
      return fileIndex + 1;
    }

    // by default select previous neighbour
    if (fileIndex > 0) {
      return fileIndex - 1;
    }
    // do nothing
    return -1;
  }

  public @NotNull FileEditorManagerImpl getManager() { return myOwner.getManager(); }

  public int getTabCount() {
    return myTabbedPane.getTabCount();
  }

  void setForegroundAt(int index, @NotNull Color color) {
    myTabbedPane.setForegroundAt(index, color);
  }

  void setTextAttributes(int index, @Nullable TextAttributes attributes) {
    myTabbedPane.setTextAttributes(index, attributes);
  }

  private void setTitleAt(int index, @NlsContexts.TabTitle @NotNull String text) {
    myTabbedPane.setTitleAt(index, text);
  }

  private void setBackgroundColorAt(int index, @Nullable Color color) {
    myTabbedPane.setBackgroundColorAt(index, color);
  }

  private void setToolTipTextAt(int index, @Nullable @NlsContexts.Tooltip String text) {
    myTabbedPane.setToolTipTextAt(index, text);
  }


  void setTabLayoutPolicy(int policy) {
    myTabbedPane.setTabLayoutPolicy(policy);
  }

  void setTabsPlacement(int tabPlacement) {
    myTabbedPane.setTabPlacement(tabPlacement);
  }

  void updateTabsLayout(@NotNull TabsLayoutInfo newTabsLayoutInfo) {
    myTabbedPane.updateTabsLayout(newTabsLayoutInfo);
  }

  public void setAsCurrentWindow(boolean requestFocus) {
    myOwner.setCurrentWindow(this, requestFocus);
  }

  void updateFileBackgroundColor(@NotNull VirtualFile file) {
    int index = findFileEditorIndex(file);
    if (index != -1) {
      Color color = ExperimentalUI.isNewEditorTabs() ? null : EditorTabPresentationUtil.getEditorTabBackgroundColor(getManager().getProject(), file);
      setBackgroundColorAt(index, color);
    }
  }

  public @NotNull EditorsSplitters getOwner() {
    return myOwner;
  }

  boolean isEmptyVisible() {
    return myTabbedPane.isEmptyVisible();
  }

  public Dimension getSize() {
    return myPanel.getSize();
  }

  public @NotNull EditorTabbedContainer getTabbedPane() {
    return myTabbedPane;
  }

  public void requestFocus(boolean forced) {
    myTabbedPane.requestFocus(forced);
  }

  public void toFront() {
    Window window = UIUtil.getWindow(myTabbedPane.getComponent());
    UIUtil.toFront(window);
  }

  protected static class TComp extends JPanel implements DataProvider, EditorWindowHolder {
    final @NotNull EditorComposite myComposite;
    protected final EditorWindow myWindow;

    TComp(@NotNull EditorWindow window, @NotNull EditorComposite composite) {
      super(new BorderLayout());
      myComposite = composite;
      myWindow = window;
      add(composite.getComponent(), BorderLayout.CENTER);
      addFocusListener(new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
          ApplicationManager.getApplication().invokeLater(() -> {
            if (!hasFocus()) return;
            JComponent focus = myComposite.getSelectedWithProvider().getFileEditor().getPreferredFocusedComponent();
            if (focus != null && !focus.hasFocus()) {
              getGlobalInstance().requestFocus(focus, true);
            }
          });
        }
      });
      setFocusTraversalPolicy(new FocusTraversalPolicy() {
        @Override
        public Component getComponentAfter(Container aContainer, Component aComponent) {
          return myComposite.getFocusComponent();
        }

        @Override
        public Component getComponentBefore(Container aContainer, Component aComponent) {
          return myComposite.getFocusComponent();
        }

        @Override
        public Component getFirstComponent(Container aContainer) {
          return myComposite.getFocusComponent();
        }

        @Override
        public Component getLastComponent(Container aContainer) {
          return myComposite.getFocusComponent();
        }

        @Override
        public Component getDefaultComponent(Container aContainer) {
          return myComposite.getFocusComponent();
        }
      });
      setFocusCycleRoot(true);
    }

    @Override
    public @NotNull EditorWindow getEditorWindow() {
      return myWindow;
    }

    @Override
    public Object getData(@NotNull String dataId) {
      if (CommonDataKeys.VIRTUAL_FILE.is(dataId)){
        VirtualFile virtualFile = myComposite.getFile();
        return virtualFile.isValid() ? virtualFile : null;
      }
      if (CommonDataKeys.PROJECT.is(dataId)) {
        return myComposite.getProject();
      }
      return null;
    }
  }

  private void checkConsistency() {
    LOG.assertTrue(myOwner.containsWindow(this), "EditorWindow not in collection");
  }

  /**
   * @deprecated Use {@link #getSelectedComposite}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2023.1")
  public @Nullable EditorWithProviderComposite getSelectedEditor() {
    return (EditorWithProviderComposite)getSelectedComposite(false);
  }

  public @Nullable EditorComposite getSelectedComposite() {
    return getSelectedComposite(false);
  }

  /**
   * @deprecated Use {@link #getSelectedComposite}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2023.1")
  public @Nullable EditorWithProviderComposite getSelectedEditor(boolean ignorePopup) {
    return (EditorWithProviderComposite)getSelectedComposite(ignorePopup);
  }

  /**
   * @param ignorePopup if {@code false} and context menu is shown currently for some tab,
   *                    composite for which menu is invoked will be returned
   */
  public @Nullable EditorComposite getSelectedComposite(boolean ignorePopup) {
    TComp comp = ObjectUtils.tryCast(myTabbedPane.getSelectedComponent(ignorePopup), TComp.class);
    return comp == null ? null : comp.myComposite;
  }

  public @NotNull List<@NotNull EditorComposite> getAllComposites() {
    return IntStream.range(0, getTabCount()).mapToObj(i -> getCompositeAt(i))
      .collect(Collectors.toList());
  }

  /**
   * @deprecated {@link #getAllComposites()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2023.1")
  public EditorWithProviderComposite @NotNull [] getEditors() {
    return ContainerUtil.filterIsInstance(getAllComposites(), EditorWithProviderComposite.class)
      .toArray(new EditorWithProviderComposite[0]);
  }

  public VirtualFile @NotNull [] getFiles() {
    return ContainerUtil.map2Array(getAllComposites(), VirtualFile.class, it -> it.getFile());
  }

  /**
   * @deprecated {@link #setSelectedComposite(EditorComposite, boolean)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2023.1")
  public void setSelectedEditor(@NotNull EditorComposite composite, boolean focusEditor) {
    setSelectedComposite(composite, focusEditor);
  }

  public void setSelectedComposite(@NotNull EditorComposite composite, boolean focusEditor) {
    // select a composite in a tabbed pane and then focus a composite if needed
    int index = findFileIndex(composite.getFile());
    if (index != -1) {
      if (!isDisposed()) {
        myTabbedPane.setSelectedIndex(index, focusEditor);
      }
    }
  }

  /**
   * @deprecated Use {@link #setComposite(EditorComposite, boolean)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2023.1")
  public void setEditor(@NotNull EditorWithProviderComposite editor, boolean focusEditor) {
    setComposite(editor, new FileEditorOpenOptions().withRequestFocus(focusEditor));
  }

  /**
   * @deprecated Use {@link #setComposite(EditorComposite, FileEditorOpenOptions)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2023.1")
  public void setEditor(@NotNull EditorWithProviderComposite editor, @NotNull FileEditorOpenOptions options) {
    setComposite(editor, options);
  }

  public void setComposite(@NotNull EditorComposite composite, boolean focusEditor) {
    setComposite(composite, new FileEditorOpenOptions().withRequestFocus(focusEditor));
  }

  public void setComposite(@NotNull EditorComposite composite, @NotNull FileEditorOpenOptions options) {
    boolean isNewEditor = findCompositeIndex(composite) == -1;
    boolean isPreviewMode = (isNewEditor || composite.isPreview()) && shouldReservePreview(composite.getFile(), options);
    boolean wasPinned = composite.isPinned();
    composite.setPreview(isPreviewMode);

    if (isNewEditor) {
      int indexToInsert = options.getIndex();

      if (indexToInsert == -1 && isPreviewMode) {
        indexToInsert = findPreviewIndex();
      }
      if (indexToInsert == -1) {
        indexToInsert = UISettings.getInstance().getOpenTabsAtTheEnd() ? myTabbedPane.getTabCount()
                                                                       : myTabbedPane.getSelectedIndex() + 1;
      }
      VirtualFile file = composite.getFile();
      Icon template = AllIcons.FileTypes.Text;
      EmptyIcon emptyIcon = EmptyIcon.create(template.getIconWidth(), template.getIconHeight());
      myTabbedPane.insertTab(file, emptyIcon, new TComp(this, composite), null, indexToInsert, composite);

      Integer dragStartIndex = null;
      Integer hash = file.getUserData(DRAG_START_LOCATION_HASH_KEY);
      if (hash != null && System.identityHashCode(myTabbedPane.getTabs()) == hash.intValue()) {
        dragStartIndex = file.getUserData(DRAG_START_INDEX_KEY);
      }
      if (dragStartIndex == null || dragStartIndex != -1) {
        Boolean initialPinned = file.getUserData(DRAG_START_PINNED_KEY);
        if (initialPinned != null) {
          composite.setPinned(initialPinned);
        }
        else if (wasPinned) {
          composite.setPinned(true);
        }
      }
      file.putUserData(DRAG_START_LOCATION_HASH_KEY, null);
      file.putUserData(DRAG_START_INDEX_KEY, null);
      file.putUserData(DRAG_START_PINNED_KEY, null);
      trimToSize(file, false);
      myOwner.updateFileIconImmediately(file, IconUtil.computeBaseFileIcon(file));
      myOwner.updateFileIconLater(file);
      myOwner.updateFileColor(file);
    }
    myOwner.updateFileColor(composite.getFile());
    if (options.getSelectAsCurrent()) {
      setSelectedComposite(composite, options.getRequestFocus());
    }
    updateTabsVisibility();
    myOwner.validate();
  }

  private boolean splitAvailable() {
    return getTabCount() >= 1;
  }

  public @Nullable EditorWindow split(int orientation, boolean forceSplit, @Nullable VirtualFile virtualFile, boolean focusNew) {
    return split(orientation, forceSplit, virtualFile, focusNew, true);
  }

  public @Nullable EditorWindow split(int orientation, boolean forceSplit, @Nullable VirtualFile virtualFile, boolean focusNew, boolean fileIsSecondaryComponent) {
    checkConsistency();
    if (!splitAvailable()) {
      return null;
    }

    FileEditorManagerImpl fileEditorManager = myOwner.getManager();
    if (!forceSplit && inSplitter()) {
      EditorWindow[] siblings = findSiblings();
      EditorWindow target = siblings[0];
      if (virtualFile != null) {
        FileEditor[] editors = fileEditorManager.openFileImpl3(target, virtualFile, focusNew, null).first;
        syncCaretIfPossible(editors);
      }
      return target;
    }

    JPanel panel = myPanel;
    panel.setBorder(null);
    int tabCount = getTabCount();
    if (tabCount != 0) {
      myPanel = new JPanel(new BorderLayout());
      myPanel.setOpaque(false);

      Splitter splitter = new OnePixelSplitter(orientation == JSplitPane.VERTICAL_SPLIT, 0.5f, 0.1f, 0.9f);
      splitter.putClientProperty(EditorsSplitters.SPLITTER_KEY, Boolean.TRUE);
      EditorWindow res = new EditorWindow(myOwner, myOwner);
      EditorComposite selectedComposite = getSelectedComposite();
      assert selectedComposite != null;

      panel.remove(myTabbedPane.getComponent());
      panel.add(splitter, BorderLayout.CENTER);
      if (fileIsSecondaryComponent) {
        splitter.setFirstComponent(myPanel);
      } else {
        splitter.setSecondComponent(myPanel);
      }
      myPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);
      if (fileIsSecondaryComponent) {
        splitter.setSecondComponent(res.myPanel);
      }
      else {
        splitter.setFirstComponent(res.myPanel);
      }
      normalizeProportionsIfNeed(myPanel);
      // open only selected file in the new splitter instead of opening all tabs
      VirtualFile file = selectedComposite.getFile();
      VirtualFile nextFile = virtualFile == null ? file : virtualFile;
      HistoryEntry currentState = selectedComposite.currentStateAsHistoryEntry();
      VirtualFile currentStateFile = currentState.getFile();
      if (currentStateFile == null || !currentStateFile.equals(nextFile)) currentState = null;
      FileEditorOpenOptions openOptions = new FileEditorOpenOptions()
        .withRequestFocus(focusNew)
        .withExactState();
      FileEditor[] editors = fileEditorManager.openFileImpl4(res, nextFile, currentState, openOptions).first;
      syncCaretIfPossible(editors);
      if (isFileOpen(nextFile)) {
        res.setFilePinned(nextFile, isFilePinned(nextFile));
      }
      if (!focusNew) {
        res.setSelectedComposite(selectedComposite, true);
        getGlobalInstance().doWhenFocusSettlesDown(() -> {
          JComponent focusComponent = selectedComposite.getFocusComponent();
          if (focusComponent != null) {
            getGlobalInstance().requestFocus(focusComponent, true);
          }
        });
      }
      panel.revalidate();
      return res;
    }
    return null;
  }

  private void normalizeProportionsIfNeed(Container comp) {
    if (!AdvancedSettings.getBoolean("editor.normalize.splits")) return;

    Boolean isVertical = null;
    if (comp instanceof Splitter) {
      isVertical = ((Splitter)comp).isVertical();
    } else if (comp.getComponents().length > 0 && comp.getComponents()[0] instanceof Splitter) {
      isVertical = ((Splitter)comp.getComponents()[0]).isVertical();
    }

    Map<Splitter, Boolean> hierarchyStack = new LinkedHashMap<>();
    while (comp != getManager().getMainSplitters() && comp != null) {
      Container parent = comp.getParent();
      if (parent instanceof Splitter) {
        if (isVertical == null) { //Stack orientation (row or column) is not yet defined
          isVertical = ((Splitter)parent).isVertical();
        } else if (isVertical != ((Splitter)parent).isVertical()) {
          break;
        }
        hierarchyStack.put((Splitter)parent, ((Splitter)parent).getFirstComponent() == comp);
      }
      comp = parent;
    }
    int i = 0;
    for (Map.Entry<Splitter, Boolean> entry : hierarchyStack.entrySet()) {
      entry.getKey().setProportion(entry.getValue() ? 1 - 1f / (2 + i) : 1f / (2 + i));
      i++;
    }
  }

  /**
   * Tries to setup caret and viewport for the given editor from the selected one.
   *
   * @param toSync    editor to setup caret and viewport for
   */
  private void syncCaretIfPossible(FileEditor @NotNull [] toSync) {
    EditorComposite from = getSelectedComposite();
    if (from == null) {
      return;
    }

    FileEditor caretSource = from.getSelectedEditor();
    if (!(caretSource instanceof TextEditor)) {
      return;
    }

    Editor editorFrom = ((TextEditor)caretSource).getEditor();
    int offset = editorFrom.getCaretModel().getOffset();
    if (offset <= 0) {
      return;
    }

    int scrollOffset = editorFrom.getScrollingModel().getVerticalScrollOffset();

    for (FileEditor fileEditor : toSync) {
      if (!(fileEditor instanceof TextEditor)) {
        continue;
      }
      Editor editor = ((TextEditor)fileEditor).getEditor();
      if (editorFrom.getDocument() == editor.getDocument()) {
        editor.getCaretModel().moveToOffset(offset);
        ScrollingModel scrollingModel = editor.getScrollingModel();
        scrollingModel.scrollVertically(scrollOffset);

        SwingUtilities.invokeLater(() -> {
          if (!editor.isDisposed()) {
            scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE);
          }
        });
      }
    }
  }

  public EditorWindow @NotNull [] findSiblings() {
    checkConsistency();
    ArrayList<EditorWindow> res = new ArrayList<>();
    if (myPanel.getParent() instanceof Splitter) {
      Splitter splitter = (Splitter)myPanel.getParent();
      for (EditorWindow win : myOwner.getWindows()) {
        if (win != this && SwingUtilities.isDescendingFrom(win.myPanel, splitter)) {
          res.add(win);
        }
      }
    }
    return res.toArray(new EditorWindow[0]);
  }

  public enum RelativePosition {
    CENTER(SwingConstants.CENTER), UP(SwingConstants.TOP), LEFT(SwingConstants.LEFT), DOWN(SwingConstants.BOTTOM), RIGHT(SwingConstants.RIGHT);

    final int mySwingConstant;

    RelativePosition(int swingConstant) {
      mySwingConstant = swingConstant;
    }
  }

  @NotNull
  public Map<RelativePosition, EditorWindow> getAdjacentEditors() {
    checkConsistency();
    final Map<RelativePosition, EditorWindow> adjacentEditors = new HashMap<>(4); //Can't have more than 4

    final List<EditorWindow> windows = myOwner.getOrderedWindows();
    windows.remove(this);
    final Map<JPanel, EditorWindow> panel2Window = new HashMap<>();
    for (EditorWindow win : windows) {
      panel2Window.put(win.myPanel, win);
    }

    final RelativePoint relativePoint = new RelativePoint(myPanel.getLocationOnScreen());
    final Point point = relativePoint.getPoint(myOwner);
    BiFunction<Integer, Integer, Component> nearestComponent = (x, y) -> SwingUtilities.getDeepestComponentAt(myOwner, x, y);
    Function<Component, EditorWindow> findAdjacentEditor = (component) -> {
      while (component != myOwner && component != null) {
        if (panel2Window.containsKey(component)) {
          return panel2Window.get(component);
        }
        component = component.getParent();
      }
      return null;
    };
    BiConsumer<EditorWindow, RelativePosition> biConsumer = (window, position) -> {
      if (window == null) {
        return;
      }
      adjacentEditors.put(position,window);
    };

    // Even if above/below adjacent editor is shifted a bit to the right from left edge of current editor,
    // still try to choose editor that is visually above/below - shifted nor more then quater of editor width.
    int x = point.x + myPanel.getWidth() / 4;
    // Splitter has width of one pixel - we need to step at least 2 pixels to be over adjacent editor
    int searchStep = 2;

    biConsumer.accept(findAdjacentEditor.apply(nearestComponent.apply(x, point.y - searchStep)), RelativePosition.UP);
    biConsumer.accept(findAdjacentEditor.apply(nearestComponent.apply(x, point.y + myPanel.getHeight() + searchStep)), RelativePosition.DOWN);
    biConsumer.accept(findAdjacentEditor.apply(nearestComponent.apply(point.x - searchStep, point.y)), RelativePosition.LEFT);
    biConsumer.accept(findAdjacentEditor.apply(nearestComponent.apply(point.x + myPanel.getWidth() + searchStep, point.y)), RelativePosition.RIGHT);

    return adjacentEditors;
  }

  private MySplitPainter myPainter = null;
  private Runnable showSplitChooser(boolean showInfoPanel) {
    myPainter = new MySplitPainter(showInfoPanel);

    Disposable disposable = Disposer.newDisposable("GlassPaneListeners");
    IdeGlassPaneUtil.find(myPanel).addPainter(myPanel, myPainter, disposable);

    myPanel.repaint();
    myPanel.setFocusable(true);
    myPanel.grabFocus();
    myPanel.setFocusTraversalKeysEnabled(false);

    final FocusAdapter focusAdapter = new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        myPanel.removeFocusListener(this);
        if (SplitterService.getInstance().myActiveWindow == EditorWindow.this) {
          SplitterService.getInstance().stopSplitChooser(true);
        }
      }
    };

    myPanel.addFocusListener(focusAdapter);

    return () -> {
      myPainter.myRectangle = null;
      myPainter = null;
      myPanel.removeFocusListener(focusAdapter);
      myPanel.setFocusable(false);
      myPanel.repaint();
      Disposer.dispose(disposable);
    };
  }

  private final class MySplitPainter extends AbstractPainter {
    private Shape myRectangle = getTabbedPane().getTabs().getDropArea();
    private boolean myShowInfoPanel;
    RelativePosition myPosition = RelativePosition.CENTER;

    private MySplitPainter(boolean showInfoPanel) {
      myShowInfoPanel = showInfoPanel;
    }

    @Override
    public boolean needsRepaint() {
      return myRectangle != null;
    }

    @Override
    public void executePaint(Component component, Graphics2D g) {
      if (myRectangle == null) {
        return;
      }
      GraphicsUtil.setupAAPainting(g);
      g.setColor(JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND);
      g.fill(myRectangle);

      if (myPosition == RelativePosition.CENTER && myShowInfoPanel) {
        drawInfoPanel(component, g);
      }
    }

    private void drawInfoPanel(Component component, Graphics2D g) {
      int centerX = myRectangle.getBounds().x + myRectangle.getBounds().width / 2;
      int centerY = myRectangle.getBounds().y + myRectangle.getBounds().height / 2;

      int height = Registry.intValue("ide.splitter.chooser.info.panel.height");
      int width = Registry.intValue("ide.splitter.chooser.info.panel.width");
      int arc = Registry.intValue("ide.splitter.chooser.info.panel.arc");

      Function<String, String> getShortcut = (actionId) -> {
        KeyboardShortcut shortcut = ActionManager.getInstance().getKeyboardShortcut(actionId);
        return KeymapUtil.getKeystrokeText(shortcut == null ? null : shortcut.getFirstKeyStroke());
      };
      String openShortcuts = String.format(IdeBundle.message("split.with.chooser.move.tab"), getShortcut.apply("SplitChooser.Split"),
                                           SplitterService.getInstance().myInitialEditorWindow != null
                                           ? String.format(IdeBundle.message("split.with.chooser.duplicate.tab"), getShortcut.apply("SplitChooser.Duplicate")) : "");
      String switchShortcuts = String.format(IdeBundle.message("split.with.chooser.switch.tab"), getShortcut.apply("SplitChooser.NextWindow"));

      // Adjust default width to info text
      Font font = StartupUiUtil.getLabelFont();
      FontMetrics fontMetrics = g.getFontMetrics(font);
      int openShortcutsWidth = fontMetrics.stringWidth(openShortcuts);
      int switchShortcutsWidth = fontMetrics.stringWidth(switchShortcuts);
      width = Math.max(width, Math.round(Math.max(openShortcutsWidth, switchShortcutsWidth) * 1.2f));

      // Check if info panel will actually fit into editor with some free space around edges
      if (myRectangle.getBounds().height < height * 1.2f || myRectangle.getBounds().width < width * 1.2f) {
        return;
      }

      final Shape rectangle = new RoundRectangle2D.Double(centerX - width / 2.0, centerY - height / 2.0, width, height, arc, arc);
      g.setColor(UIUtil.getLabelBackground());
      g.fill(rectangle);

      int arrowsCenterVShift = Registry.intValue("ide.splitter.chooser.info.panel.arrows.shift.center");
      int arrowsVShift = Registry.intValue("ide.splitter.chooser.info.panel.arrows.shift.vertical");
      int arrowsHShift = Registry.intValue("ide.splitter.chooser.info.panel.arrows.shift.horizontal");
      Function<Icon, Point> function = (icon) -> new Point(centerX - icon.getIconWidth() / 2, centerY - icon.getIconHeight() / 2 + arrowsCenterVShift);
      Point forUpDownIcons = function.apply(AllIcons.Chooser.Top);
      AllIcons.Chooser.Top.paintIcon(component, g, forUpDownIcons.x, forUpDownIcons.y - arrowsVShift);
      AllIcons.Chooser.Bottom.paintIcon(component, g, forUpDownIcons.x, forUpDownIcons.y + arrowsVShift);
      Point forLeftRightIcons = function.apply(AllIcons.Chooser.Right);
      AllIcons.Chooser.Right.paintIcon(component, g, forLeftRightIcons.x + arrowsHShift, forLeftRightIcons.y);
      AllIcons.Chooser.Left.paintIcon(component, g, forLeftRightIcons.x - arrowsHShift, forLeftRightIcons.y);

      int textVShift = Registry.intValue("ide.splitter.chooser.info.panel.text.shift");
      int textY = forUpDownIcons.y + AllIcons.Chooser.Bottom.getIconHeight() + textVShift;
      g.setColor(UIUtil.getInactiveTextColor());
      g.setFont(font);
      g.drawString(openShortcuts, centerX - openShortcutsWidth / 2, textY);
      if (EditorWindow.this.getOwner().getWindows().length > 1) {
        g.drawString(switchShortcuts, centerX - switchShortcutsWidth / 2, textY + fontMetrics.getHeight());
      }
    }

    private void positionChanged(RelativePosition position) {
      if (myPosition == position) {
        return;
      }
      myShowInfoPanel = false;
      myPosition = position;
      myRectangle = null;
      setNeedsRepaint(true);

      Rectangle r = getTabbedPane().getTabs().getDropArea();
      TabsUtil.updateBoundsWithDropSide(r, myPosition.mySwingConstant);
      myRectangle = new Rectangle2D.Double(r.x, r.y, r.width, r.height);
    }
  }

  @Service
  public static final class SplitterService {
    private EditorWindow myActiveWindow = null;
    private VirtualFile myFile = null;
    private Runnable mySplitChooserDisposer = null;
    private EditorWindow myInitialEditorWindow = null;

    public void activateSplitChooser(@NotNull EditorWindow window, @NotNull VirtualFile file, boolean openedFromEditor) {
      if (isActive()) {
        stopSplitChooser(true);
      }
      myActiveWindow = window;
      myFile = file;
      if (openedFromEditor) {
        myInitialEditorWindow = myActiveWindow;
      }
      mySplitChooserDisposer = myActiveWindow.showSplitChooser(true);
    }

    public void switchWindow(@NotNull EditorWindow window) {
      if (mySplitChooserDisposer != null) {
        mySplitChooserDisposer.run();
      }
      myActiveWindow = window;
      mySplitChooserDisposer = myActiveWindow.showSplitChooser(false);
    }

    public void stopSplitChooser(boolean interrupted) {
      EditorWindow activeWindow = myActiveWindow;
      myActiveWindow = null;
      myFile = null;
      mySplitChooserDisposer.run();
      mySplitChooserDisposer = null;
      myInitialEditorWindow = null;
      if (!interrupted) {
        activeWindow.requestFocus(true);
      }
    }

    public static SplitterService getInstance() {
      return ApplicationManager.getApplication().getService(SplitterService.class);
    }

    public boolean isActive() {
      return myActiveWindow != null;
    }

    public void nextWindow() {
      if (!isActive()) {
        return;
      }

      List<EditorWindow> orderedWindows = myActiveWindow.getOwner().getOrderedWindows();
      int index = (orderedWindows.indexOf(myActiveWindow) + 1) % orderedWindows.size();
      switchWindow(orderedWindows.get(index));
    }

    public void previousWindow() {
      if (!isActive()) {
        return;
      }

      List<EditorWindow> orderedWindows = myActiveWindow.getOwner().getOrderedWindows();
      int index = orderedWindows.indexOf(myActiveWindow) - 1;
      index = index < 0 ? orderedWindows.size() - 1 : index;
      switchWindow(orderedWindows.get(index));
    }

    public void split(boolean move) {
      final EditorWindow activeWindow = myActiveWindow;
      final EditorWindow initialWindow = myInitialEditorWindow;
      final VirtualFile file = myFile;
      final RelativePosition position = activeWindow.myPainter.myPosition;

      stopSplitChooser(false);

      // If a position is default and focus is still in the same editor window => nothing need to be done
      if (position != RelativePosition.CENTER || initialWindow != activeWindow) {
        if (position == RelativePosition.CENTER) {
          final FileEditorManagerEx fileEditorManager = activeWindow.getManager();
          fileEditorManager.openFile(file, true);
        }
        else {
          activeWindow.split(
            position == RelativePosition.UP || position == RelativePosition.DOWN ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT,
            true, file, true,
            position != RelativePosition.LEFT && position != RelativePosition.UP);
        }

        if (initialWindow != null && move) {
          initialWindow.closeFile(file, true ,false);
        }
      }
    }

    public void setSplitSide(RelativePosition side) {
      if (side != myActiveWindow.myPainter.myPosition) {
        myActiveWindow.myPainter.positionChanged(side);
      } else {
        final Map<RelativePosition, EditorWindow> editors = myActiveWindow.getAdjacentEditors();
        if (editors.containsKey(side)) {
          if (!isActive()) {
            return;
          }

          switchWindow(editors.get(side));
        }
      }
    }
  }

  void changeOrientation() {
    checkConsistency();
    Container parent = myPanel.getParent();
    if (parent instanceof Splitter) {
      Splitter splitter = (Splitter)parent;
      splitter.setOrientation(!splitter.getOrientation());
    }
  }

  private int findFileEditorIndex(@NotNull VirtualFile file) {
    @Nullable EditorComposite composite = getComposite(file);
    return composite == null ? -1 : findCompositeIndex(composite);
  }

  void updateFileIcon(@NotNull VirtualFile file, @NotNull Icon icon) {
    EditorComposite composite = getComposite(file);
    if (composite == null) return;
    int index = findCompositeIndex(composite);
    if (index < 0) return;
    myTabbedPane.setIconAt(index, decorateFileIcon(composite, icon));
  }

  void updateFileName(@NotNull VirtualFile file) {
    int index = findFileEditorIndex(file);
    if (index == -1) return;
    ReadAction.nonBlocking(() -> EditorTabPresentationUtil.getEditorTabTitle(getManager().getProject(), file))
      .expireWhen(this::isDisposed)
      .coalesceBy(this)
      .finishOnUiThread(ModalityState.any(), (@NlsContexts.TabTitle String title) -> {
        int index1 = findFileEditorIndex(file);
        if (index1 != -1) setTitleAt(index1, title);
      })
      .submit(AppExecutorUtil.getAppExecutorService());
    setToolTipTextAt(index, UISettings.getInstance().getShowTabsTooltips() ? getManager().getFileTooltipText(file) : null);
  }

  @Nullable
  private static Icon decorateFileIcon(@NotNull EditorComposite composite, @NotNull Icon baseIcon) {
    UISettings settings = UISettings.getInstance();
    boolean showAsterisk = settings.getMarkModifiedTabsWithAsterisk() && composite.isModified();
    boolean showFileIconInTabs = UISettings.getInstance().getShowFileIconInTabs();
    if (!showAsterisk) {
      return showFileIconInTabs ? baseIcon : null;
    }

    Icon modifiedIcon = IconUtil.cropIcon(AllIcons.General.Modified, new JBRectangle(3, 3, 7, 7));
    LayeredIcon result = new LayeredIcon(2);
    if (showFileIconInTabs) {
      result.setIcon(baseIcon, 0);
      result.setIcon(modifiedIcon, 1, -modifiedIcon.getIconWidth() / 2, 0);
    } else {
      result.setIcon(EmptyIcon.create(modifiedIcon.getIconWidth(), baseIcon.getIconHeight()), 0);
      result.setIcon(modifiedIcon, 1, 0, 0);

    }
    return JBUIScale.scaleIcon(result);
  }

  public void unsplit(boolean setCurrent) {
    checkConsistency();
    Container splitter = myPanel.getParent();

    if (!(splitter instanceof Splitter)) return;

    EditorComposite compositeToSelect = getSelectedComposite();
    EditorWindow[] siblings = findSiblings();
    JPanel parent = (JPanel)splitter.getParent();

    for (EditorWindow eachSibling : siblings) {
      // selected editors will be added first
      EditorComposite selected = eachSibling.getSelectedComposite();
      if (compositeToSelect == null && selected != null) {
        compositeToSelect = selected;
        break;
      }
    }

    FileEditorOpenOptions openOptions = new FileEditorOpenOptions()
      .withSelectAsCurrent(false) // we'll select and focus a single editor in the end
      .withRequestFocus(false);
    for (EditorWindow sibling : siblings) {
      List<EditorComposite> siblingEditors = sibling.getAllComposites();
      for (EditorComposite siblingEditor : siblingEditors) {
        if (compositeToSelect == null) {
          compositeToSelect = siblingEditor;
        }
        processSiblingComposite(siblingEditor, openOptions);
      }
      LOG.assertTrue(sibling != this);
      sibling.dispose();
    }
    parent.remove(splitter);
    parent.add(myTabbedPane.getComponent(), BorderLayout.CENTER);
    parent.revalidate();
    myPanel = parent;
    if (compositeToSelect != null) {
      setSelectedComposite(compositeToSelect, true);
    }
    if (setCurrent) {
      myOwner.setCurrentWindow(this, false);
    }
    normalizeProportionsIfNeed(myPanel);
  }

  private void processSiblingComposite(@NotNull EditorComposite composite,
                                       @NotNull FileEditorOpenOptions openOptions) {
    if (getTabCount() < UISettings.getInstance().getState().getEditorTabLimit() &&
        getComposite(composite.getFile()) == null) {
      setComposite(composite, openOptions);
    }
    else {
      getManager().disposeComposite(composite);
    }
  }

  void unsplitAll() {
    checkConsistency();
    while (inSplitter()) {
      unsplit(true);
    }
  }

  public boolean inSplitter() {
    checkConsistency();
    return myPanel.getParent() instanceof Splitter;
  }

  public VirtualFile getSelectedFile() {
    checkConsistency();
    EditorComposite composite = getSelectedComposite();
    return composite == null ? null : composite.getFile();
  }

  /**
   * @deprecated Use {@link #getComposite(VirtualFile)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2023.1")
  public @Nullable EditorWithProviderComposite findFileComposite(@NotNull VirtualFile file) {
    return (EditorWithProviderComposite)getComposite(file);
  }

  public @Nullable EditorComposite getComposite(@NotNull VirtualFile file) {
    if (file instanceof BackedVirtualFile) {
      file = ((BackedVirtualFile)file).getOriginFile();
    }

    for (int i = 0; i != getTabCount(); ++i) {
      EditorComposite composite = getCompositeAt(i);
      if (composite.getFile().equals(file)) {
        return composite;
      }
    }
    return null;
  }


  private int findComponentIndex(@NotNull Component component) {
    for (int i = 0; i != getTabCount(); ++i) {
      EditorComposite composite = getCompositeAt(i);
      if (composite.getComponent().equals(component)) {
        return i;
      }
    }
    return -1;
  }

  private int findPreviewIndex() {
    for (int i = getTabCount() - 1; i >= 0; --i) {
      EditorComposite composite = getCompositeAt(i);
      if (composite.isPreview()) {
        return i;
      }
    }
    return -1;
  }

  int findCompositeIndex(@NotNull EditorComposite composite) {
    for (int i = 0; i != getTabCount(); ++i) {
      EditorComposite compositeAt = getCompositeAt(i);
      if (compositeAt.equals(composite)) {
        return i;
      }
    }
    return -1;
  }

  int findFileIndex(@NotNull VirtualFile fileToFind) {
    for (int i = 0; i != getTabCount(); ++i) {
      VirtualFile file = getFileAt(i);
      if (file.equals(fileToFind)) {
        return i;
      }
    }
    return -1;
  }

  @NotNull
  private EditorComposite getCompositeAt(int i) {
    return ((TComp)myTabbedPane.getComponentAt(i)).myComposite;
  }

  public boolean isFileOpen(@NotNull VirtualFile file) {
    return getComposite(file) != null;
  }

  public boolean isFilePinned(@NotNull VirtualFile file) {
    EditorComposite composite = getComposite(file);
    if (composite == null) {
      throw new IllegalArgumentException("file is not open: " + file.getPath());
    }
    return composite.isPinned();
  }

  public void setFilePinned(@NotNull VirtualFile file, boolean pinned) {
    EditorComposite composite = getComposite(file);
    if (composite == null) {
      throw new IllegalArgumentException("file is not open: " + file.getPath());
    }
    boolean wasPinned = composite.isPinned();
    composite.setPinned(pinned);
    if (composite.isPreview()) {
      composite.setPreview(false);
      myOwner.updateFileColor(file);
    }
    if (wasPinned != pinned && ApplicationManager.getApplication().isDispatchThread()) {
      ObjectUtils.consumeIfCast(getTabbedPane().getTabs(), JBTabsImpl.class, JBTabsImpl::doLayout);
    }
  }

  void trimToSize(@Nullable VirtualFile fileToIgnore, boolean transferFocus) {
    getManager().getReady(this).doWhenDone(() -> {
      if (!isDisposed()) {
        doTrimSize(fileToIgnore, UISettings.getInstance().getState().getCloseNonModifiedFilesFirst(), transferFocus);
      }
    });
  }

  private void doTrimSize(@Nullable VirtualFile fileToIgnore, boolean closeNonModifiedFilesFirst, boolean transferFocus) {
    int limit = getTabLimit();
    Set<VirtualFile> closingOrder = getTabClosingOrder(closeNonModifiedFilesFirst);
    VirtualFile selectedFile = getSelectedFile();
    if (selectedFile != null && shouldCloseSelected(selectedFile, fileToIgnore)) {
      defaultCloseFile(selectedFile, transferFocus);
      closingOrder.remove(selectedFile);
    }

    // close all preview tabs except one if exists
    Set<VirtualFile> previews =
      getAllComposites().stream().filter(EditorComposite::isPreview).map(EditorComposite::getFile).collect(Collectors.toSet());
    var survivedPreviewFile = previews.contains(fileToIgnore) ? fileToIgnore : previews.stream().findAny().orElse(null);
    for (VirtualFile preview : previews) {
      if (!Objects.equals(preview, survivedPreviewFile)) defaultCloseFile(preview, transferFocus);
    }

    for (VirtualFile file : closingOrder) {
      if (myTabbedPane.getTabCount() <= limit || myTabbedPane.getTabCount() == 0 || areAllTabsPinned(fileToIgnore)) {
        return;
      }
      if (fileCanBeClosed(file, fileToIgnore)) {
        defaultCloseFile(file, transferFocus);
      }
    }
  }

  public static int getTabLimit() {
    int limit = UISettings.getInstance().getEditorTabLimit();
    if (ToggleDistractionFreeModeAction.isDistractionFreeModeEnabled()
        && ToggleDistractionFreeModeAction.getStandardTabPlacement() == UISettings.TABS_NONE) {
      limit = 1;
    }
    return limit;
  }

  private @NotNull Set<VirtualFile> getTabClosingOrder(boolean closeNonModifiedFilesFirst) {
    VirtualFile[] allFiles = getFiles();
    List<VirtualFile> histFiles = EditorHistoryManager.getInstance(getManager().getProject()).getFileList();

    Set<VirtualFile> closingOrder = new LinkedHashSet<>();

    // first, we search for files not in history
    for (VirtualFile file : allFiles) {
      if (!histFiles.contains(file)) {
        closingOrder.add(file);
      }
    }

    if (closeNonModifiedFilesFirst) {
      // Search in history
      for (VirtualFile file : histFiles) {
        EditorComposite composite = getComposite(file);
        if (composite != null && !myOwner.getManager().isChanged(composite)) {
          // we found non modified file
          closingOrder.add(file);
        }
      }

      // Search in tabbed pane
      for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
        VirtualFile file = getFileAt(i);
        if (!myOwner.getManager().isChanged(getCompositeAt(i))) {
          // we found non modified file
          closingOrder.add(file);
        }
      }
    }

    // If it's not enough to close non-modified files only, try all other files.
    // Search in history from less frequently used.
    closingOrder.addAll(histFiles);

    // finally, close tabs by their order
    for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
      closingOrder.add(getFileAt(i));
    }

    VirtualFile selectedFile = getSelectedFile();
    closingOrder.remove(selectedFile);
    closingOrder.add(selectedFile); // selected should be closed last
    return closingOrder;
  }

  private boolean shouldCloseSelected(@NotNull VirtualFile file, @Nullable VirtualFile fileToIgnore) {
    if (!UISettings.getInstance().getReuseNotModifiedTabs() || !myOwner.getManager().getProject().isInitialized()) {
      return false;
    }

    if (!isFileOpen(file) || isFilePinned(file)) {
      return false;
    }

    if (file.equals(fileToIgnore)) return false;

    EditorComposite composite = getComposite(file);
    if (composite == null) return false;
    //Don't check focus in unit test mode
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      Component owner = IdeFocusManager.getInstance(myOwner.getManager().getProject()).getFocusOwner();
      if (owner == null || !SwingUtilities.isDescendingFrom(owner, composite.getSelectedEditor().getComponent())) return false;
    }
    return !myOwner.getManager().isChanged(composite);
  }

  private boolean areAllTabsPinned(@Nullable VirtualFile fileToIgnore) {
    for (int i = myTabbedPane.getTabCount() - 1; i >= 0; i--) {
      if (fileCanBeClosed(getFileAt(i), fileToIgnore)) {
        return false;
      }
    }
    return true;
  }

  private boolean shouldReservePreview(@NotNull VirtualFile file,
                                       @NotNull FileEditorOpenOptions options) {
    if (!UISettings.getInstance().getOpenInPreviewTabIfPossible()) {
      return false;
    }
    if (FileEditorManagerImpl.FORBID_PREVIEW_TAB.get(file, false)) {
      return false;
    }

    if (options.getUsePreviewTab()) {
      return true;
    }

    if (!options.getSelectAsCurrent() || options.getRequestFocus()) {
      return false;
    }
    Component focusOwner = IdeFocusManager.getInstance(myOwner.getManager().getProject()).getFocusOwner();
    return hasClientPropertyInHierarchy(focusOwner, FileEditorManagerImpl.OPEN_IN_PREVIEW_TAB);
  }

  private static boolean hasClientPropertyInHierarchy(@Nullable Component owner,
                                                      @SuppressWarnings("SameParameterValue") @NotNull Key<Boolean> propertyKey) {
    Component parent = JBIterable.generate(owner, child -> child.getParent()).find(component -> {
      if (component instanceof JComponent) {
        return Boolean.TRUE.equals(((JComponent)component).getClientProperty(propertyKey));
      }
      return false;
    });
    return parent != null;
  }

  private void defaultCloseFile(@NotNull VirtualFile file, boolean transferFocus) {
    closeFile(file, true, transferFocus);
  }

  private boolean fileCanBeClosed(@NotNull VirtualFile file, @Nullable VirtualFile fileToIgnore) {
    return isFileOpen(file) && !file.equals(fileToIgnore) && !isFilePinned(file);
  }

  @NotNull VirtualFile getFileAt(int i) {
    return getCompositeAt(i).getFile();
  }

  @Override
  public String toString() {
    return "EditorWindow: files=" + Arrays.asList(getFiles());
  }
}
