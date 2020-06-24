// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ToggleDistractionFreeModeAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutInfo;
import com.intellij.util.IconUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBRectangle;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;
import java.util.*;

import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

public final class EditorWindow {
  private static final Logger LOG = Logger.getInstance(EditorWindow.class);

  public static final DataKey<EditorWindow> DATA_KEY = DataKey.create("editorWindow");

  JPanel myPanel;
  private final @NotNull EditorTabbedContainer myTabbedPane;
  @NotNull
  private final EditorsSplitters myOwner;

  private boolean myIsDisposed;
  public static final Key<Integer> INITIAL_INDEX_KEY = Key.create("initial editor index");
  private final Stack<Pair<String, FileEditorOpenOptions>> myRemovedTabs = new Stack<Pair<String, FileEditorOpenOptions>>() {
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
    updateTabsVisibility(UISettings.getInstance());
  }

  void updateTabsVisibility(@NotNull UISettings settings) {
    myTabbedPane.getTabs().getPresentation().setHideTabs(settings.getEditorTabPlacement() == UISettings.TABS_NONE || settings.getPresentationMode());
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
                                 new FileEditorOpenOptions()
                                   .withPin(info.getSecond().getPin())
                                   .withCurrentTab(true)
                                   .withFocusEditor(true)
                                   .withIndex(info.getSecond().getIndex()));
    }
  }

  public void closeFile(@NotNull VirtualFile file, boolean disposeIfNeeded, boolean transferFocus) {
    FileEditorManagerImpl editorManager = getManager();
    FileEditorManagerImpl.runBulkTabChange(myOwner, splitters -> {
      List<EditorWithProviderComposite> editors = splitters.findEditorComposites(file);
      if (editors.isEmpty()) return;
      try {
        EditorWithProviderComposite editor = findFileComposite(file);

        FileEditorManagerListener.Before beforePublisher =
          editorManager.getProject().getMessageBus().syncPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER);

        beforePublisher.beforeFileClosed(editorManager, file);

        if (editor != null) {
          int componentIndex = findComponentIndex(editor.getComponent());
          if (componentIndex >= 0) { // editor could close itself on decomposition
            int indexToSelect = calcIndexToSelect(file, componentIndex);
            FileEditorOpenOptions options = new FileEditorOpenOptions().withIndex(componentIndex).withPin(editor.isPinned());
            Pair<String, FileEditorOpenOptions> pair = Pair.create(file.getUrl(), options);
            myRemovedTabs.push(pair);
            myTabbedPane.removeTabAt(componentIndex, indexToSelect, transferFocus);
            editorManager.disposeComposite(editor);
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
        EditorWithProviderComposite editor = findFileComposite(histFile);
        if (editor == null) {
          continue; // ????
        }
        int histFileIndex = findComponentIndex(editor.getComponent());
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

  void setWaveColor(int index, @Nullable Color color) {
    myTabbedPane.setWaveColor(index, color);
  }

  private void setTitleAt(int index, @NotNull String text) {
    myTabbedPane.setTitleAt(index, text);
  }

  private void setBackgroundColorAt(int index, @Nullable Color color) {
    myTabbedPane.setBackgroundColorAt(index, color);
  }

  private void setToolTipTextAt(int index, @Nullable String text) {
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
    int index = findEditorIndex(findFileComposite(file));
    if (index != -1) {
      Color color = EditorTabPresentationUtil.getEditorTabBackgroundColor(getManager().getProject(), file, this);
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

  public boolean isValid() {
    return myPanel.isShowing();
  }

  protected static class TComp extends JPanel implements DataProvider, EditorWindowHolder {
    final @NotNull EditorWithProviderComposite myEditor;
    protected final EditorWindow myWindow;

    TComp(@NotNull EditorWindow window, @NotNull EditorWithProviderComposite editor) {
      super(new BorderLayout());
      myEditor = editor;
      myWindow = window;
      add(editor.getComponent(), BorderLayout.CENTER);
      addFocusListener(new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
          ApplicationManager.getApplication().invokeLater(() -> {
            if (!hasFocus()) return;
            JComponent focus = myEditor.getSelectedWithProvider().getFileEditor().getPreferredFocusedComponent();
            if (focus != null && !focus.hasFocus()) {
              getGlobalInstance().requestFocus(focus, true);
            }
          });
        }
      });
      setFocusTraversalPolicy(new FocusTraversalPolicy() {
        @Override
        public Component getComponentAfter(Container aContainer, Component aComponent) {
          return myEditor.getFocusComponent();
        }

        @Override
        public Component getComponentBefore(Container aContainer, Component aComponent) {
          return myEditor.getFocusComponent();
        }

        @Override
        public Component getFirstComponent(Container aContainer) {
          return myEditor.getFocusComponent();
        }

        @Override
        public Component getLastComponent(Container aContainer) {
          return myEditor.getFocusComponent();
        }

        @Override
        public Component getDefaultComponent(Container aContainer) {
          return myEditor.getFocusComponent();
        }
      });
    }

    @Override
    public @NotNull EditorWindow getEditorWindow() {
      return myWindow;
    }

    @Override
    public Object getData(@NotNull String dataId) {
      if (CommonDataKeys.VIRTUAL_FILE.is(dataId)){
        VirtualFile virtualFile = myEditor.getFile();
        return virtualFile.isValid() ? virtualFile : null;
      }
      if (CommonDataKeys.PROJECT.is(dataId)) {
        return myEditor.getFileEditorManager().getProject();
      }
      return null;
    }
  }

  private void checkConsistency() {
    LOG.assertTrue(myOwner.containsWindow(this), "EditorWindow not in collection");
  }

  public @Nullable EditorWithProviderComposite getSelectedEditor() {
    return getSelectedEditor(false);
  }

  /**
   * @param ignorePopup if {@code false} and context menu is shown currently for some tab,
   *                    editor for which menu is invoked will be returned
   */
  public @Nullable EditorWithProviderComposite getSelectedEditor(boolean ignorePopup) {
    TComp comp = ObjectUtils.tryCast(myTabbedPane.getSelectedComponent(ignorePopup), TComp.class);
    return comp == null ? null : comp.myEditor;
  }

  public EditorWithProviderComposite @NotNull [] getEditors() {
    int tabCount = getTabCount();
    EditorWithProviderComposite[] res = new EditorWithProviderComposite[tabCount];
    for (int i = 0; i != tabCount; ++i) {
      res[i] = getEditorAt(i);
    }
    return res;
  }

  public VirtualFile @NotNull [] getFiles() {
    int tabCount = getTabCount();
    VirtualFile[] res = new VirtualFile[tabCount];
    for (int i = 0; i != tabCount; ++i) {
      res[i] = getEditorAt(i).getFile();
    }
    return res;
  }

  public void setSelectedEditor(@NotNull EditorComposite editor, boolean focusEditor) {
    // select an editor in a tabbed pane and then focus an editor if needed
    int index = findFileIndex(editor.getFile());
    if (index != -1) {
        if (!isDisposed()) {
          myTabbedPane.setSelectedIndex(index, focusEditor);
        }
    }
  }

  public void setEditor(@Nullable EditorWithProviderComposite editor, boolean focusEditor) {
    setEditor(editor, true, focusEditor);
  }

  public void setEditor(@Nullable EditorWithProviderComposite editor, boolean selectEditor, boolean focusEditor) {
    if (editor != null) {
      int index = findEditorIndex(editor);
      if (index != -1) {
        if (selectEditor) {
          setSelectedEditor(editor, focusEditor);
        }
      }
      else {
        int indexToInsert;

        Integer initialIndex = editor.getFile().getUserData(INITIAL_INDEX_KEY);
        if (initialIndex != null) {
          indexToInsert = initialIndex;
        }
        else if (UISettings.getInstance().getOpenTabsAtTheEnd()) {
          indexToInsert = myTabbedPane.getTabCount();
        }
        else {
          int selectedIndex = myTabbedPane.getSelectedIndex();
          if (selectedIndex >= 0) {
            indexToInsert = selectedIndex + 1;
          }
          else {
            indexToInsert = 0;
          }
        }

        VirtualFile file = editor.getFile();
        Icon template = AllIcons.FileTypes.Text;
        EmptyIcon emptyIcon = EmptyIcon.create(template.getIconWidth(), template.getIconHeight());
        myTabbedPane.insertTab(file, emptyIcon, new TComp(this, editor), null, indexToInsert, editor);
        trimToSize(file, false);
        if (selectEditor) {
          setSelectedEditor(editor, focusEditor);
        }
        myOwner.updateFileIconImmediately(file, IconUtil.computeBaseFileIcon(file));
        myOwner.updateFileIconLater(file);
        myOwner.updateFileColor(file);
      }
      myOwner.setCurrentWindow(this, false);
    }
    myOwner.validate();
  }

  private boolean splitAvailable() {
    return getTabCount() >= 1;
  }

  public @Nullable EditorWindow split(int orientation, boolean forceSplit, @Nullable VirtualFile virtualFile, boolean focusNew) {
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
      EditorWindow res = new EditorWindow(myOwner, myOwner.parentDisposable);
      EditorWithProviderComposite selectedEditor = getSelectedEditor();
      panel.remove(myTabbedPane.getComponent());
      panel.add(splitter, BorderLayout.CENTER);
      splitter.setFirstComponent(myPanel);
      myPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);
      splitter.setSecondComponent(res.myPanel);
      // open only selected file in the new splitter instead of opening all tabs
      VirtualFile file = selectedEditor.getFile();
      if (virtualFile == null) {
        for (FileEditorAssociateFinder finder : FileEditorAssociateFinder.EP_NAME.getExtensionList()) {
          VirtualFile associatedFile = finder.getAssociatedFileToOpen(fileEditorManager.getProject(), file);

          if (associatedFile != null) {
            virtualFile = associatedFile;
            break;
          }
        }
      }

      VirtualFile nextFile = virtualFile == null ? file : virtualFile;
      HistoryEntry currentState = selectedEditor.currentStateAsHistoryEntry();
      FileEditor[] editors = fileEditorManager.openFileImpl4(res, nextFile, currentState,
                                                                   new FileEditorOpenOptions()
                                                                     .withCurrentTab(true)
                                                                     .withFocusEditor(focusNew)
                                                                     .withExactState()).first;
      syncCaretIfPossible(editors);
      res.setFilePinned(nextFile, isFilePinned(file));
      if (!focusNew) {
        res.setSelectedEditor(selectedEditor, true);
        getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(selectedEditor.getFocusComponent(), true));
      }
      panel.revalidate();
      return res;
    }
    return null;
  }

  /**
   * Tries to setup caret and viewport for the given editor from the selected one.
   *
   * @param toSync    editor to setup caret and viewport for
   */
  private void syncCaretIfPossible(FileEditor @NotNull [] toSync) {
    EditorWithProviderComposite from = getSelectedEditor();
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

  void changeOrientation() {
    checkConsistency();
    Container parent = myPanel.getParent();
    if (parent instanceof Splitter) {
      Splitter splitter = (Splitter)parent;
      splitter.setOrientation(!splitter.getOrientation());
    }
  }

  private void updateFileIconDecoration(@NotNull VirtualFile file) {
    EditorWithProviderComposite composite = Objects.requireNonNull(findFileComposite(file));
    int index = findEditorIndex(composite);
    LOG.assertTrue(index != -1);
    Icon current = myTabbedPane.getIconAt(index);
    if (current instanceof DecoratedTabIcon) {
      current = ((DecoratedTabIcon)current).fileIcon;
    }
    myTabbedPane.setIconAt(index, decorateFileIcon(composite, current));
  }

  void updateFileIcon(@NotNull VirtualFile file, @NotNull Icon icon) {
    EditorWithProviderComposite composite = Objects.requireNonNull(findFileComposite(file));
    int index = findEditorIndex(composite);
    LOG.assertTrue(index != -1);
    myTabbedPane.setIconAt(index, decorateFileIcon(composite, icon));
  }

  void updateFileName(@NotNull VirtualFile file) {
    int index = findEditorIndex(findFileComposite(file));
    if (index != -1) {
      setTitleAt(index, EditorTabPresentationUtil.getEditorTabTitle(getManager().getProject(), file, this));
      setToolTipTextAt(index, UISettings.getInstance().getShowTabsTooltips()
                              ? getManager().getFileTooltipText(file)
                              : null);
    }
  }

  /**
   * @return baseIcon augmented with pin/modification status
   */
  private static Icon decorateFileIcon(@NotNull EditorComposite composite, @NotNull Icon baseIcon) {
    int count = 1;

    Icon pinIcon;
    if (composite.isPinned()) {
      count++;
      pinIcon = AllIcons.Nodes.TabPin;
    }
    else {
      pinIcon = null;
    }

    Icon modifiedIcon;
    UISettings settings = UISettings.getInstance();
    if (settings.getMarkModifiedTabsWithAsterisk()) {
      Icon crop = IconUtil.cropIcon(AllIcons.General.Modified, new JBRectangle(3, 3, 7, 7));
      modifiedIcon = settings.getMarkModifiedTabsWithAsterisk() && composite.isModified() ? crop : new EmptyIcon(7, 7);
      count++;
    }
    else {
      modifiedIcon = null;
    }

    if (count == 1) return baseIcon;

    int i = 0;
    DecoratedTabIcon result = new DecoratedTabIcon(count, baseIcon);
    result.setIcon(baseIcon, i++);
    if (pinIcon != null) result.setIcon(pinIcon, i++);
    if (modifiedIcon != null) result.setIcon(modifiedIcon, i, -modifiedIcon.getIconWidth() / 2, 0);

    return JBUI.scale(result);
  }

  private static class DecoratedTabIcon extends LayeredIcon {
    final Icon fileIcon;

    DecoratedTabIcon(int layerCount, Icon fileIcon) {
      super(layerCount);
      this.fileIcon = fileIcon;
    }
  }

  public void unsplit(boolean setCurrent) {
    checkConsistency();
    Container splitter = myPanel.getParent();

    if (!(splitter instanceof Splitter)) return;

    EditorWithProviderComposite editorToSelect = getSelectedEditor();
    EditorWindow[] siblings = findSiblings();
    JPanel parent = (JPanel)splitter.getParent();

    for (EditorWindow eachSibling : siblings) {
      // selected editors will be added first
      EditorWithProviderComposite selected = eachSibling.getSelectedEditor();
      if (editorToSelect == null) {
        editorToSelect = selected;
      }
    }

    for (EditorWindow sibling : siblings) {
      EditorWithProviderComposite[] siblingEditors = sibling.getEditors();
      for (EditorWithProviderComposite siblingEditor : siblingEditors) {
        if (editorToSelect == null) {
          editorToSelect = siblingEditor;
        }
        processSiblingEditor(siblingEditor);
      }
      LOG.assertTrue(sibling != this);
      sibling.dispose();
    }
    parent.remove(splitter);
    parent.add(myTabbedPane.getComponent(), BorderLayout.CENTER);
    parent.revalidate();
    myPanel = parent;
    if (editorToSelect != null) {
      setSelectedEditor(editorToSelect, true);
    }
    if (setCurrent) {
      myOwner.setCurrentWindow(this, false);
    }
  }

  private void processSiblingEditor(@NotNull EditorWithProviderComposite siblingEditor) {
    if (getTabCount() < UISettings.getInstance().getState().getEditorTabLimit() &&
        findFileComposite(siblingEditor.getFile()) == null) {
      setEditor(siblingEditor, true);
    }
    else {
      getManager().disposeComposite(siblingEditor);
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
    EditorWithProviderComposite editor = getSelectedEditor();
    return editor == null ? null : editor.getFile();
  }

  public @Nullable EditorWithProviderComposite findFileComposite(@NotNull VirtualFile file) {
    if (file instanceof BackedVirtualFile) {
      file = ((BackedVirtualFile)file).getOriginFile();
    }

    for (int i = 0; i != getTabCount(); ++i) {
      EditorWithProviderComposite editor = getEditorAt(i);
      if (editor.getFile().equals(file)) {
        return editor;
      }
    }
    return null;
  }


  private int findComponentIndex(@NotNull Component component) {
    for (int i = 0; i != getTabCount(); ++i) {
      EditorWithProviderComposite editor = getEditorAt(i);
      if (editor.getComponent().equals(component)) {
        return i;
      }
    }
    return -1;
  }

  int findEditorIndex(EditorComposite editorToFind) {
    for (int i = 0; i != getTabCount(); ++i) {
      EditorWithProviderComposite editor = getEditorAt(i);
      if (editor.equals(editorToFind)) {
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
  private EditorWithProviderComposite getEditorAt(int i) {
    return ((TComp)myTabbedPane.getComponentAt(i)).myEditor;
  }

  public boolean isFileOpen(@NotNull VirtualFile file) {
    return findFileComposite(file) != null;
  }

  public boolean isFilePinned(@NotNull VirtualFile file) {
    EditorComposite editorComposite = findFileComposite(file);
    if (editorComposite == null) {
      throw new IllegalArgumentException("file is not open: " + file.getPath());
    }
    return editorComposite.isPinned();
  }

  public void setFilePinned(@NotNull VirtualFile file, boolean pinned) {
    EditorComposite editorComposite = findFileComposite(file);
    if (editorComposite == null) {
      throw new IllegalArgumentException("file is not open: " + file.getPath());
    }
    boolean wasPinned = editorComposite.isPinned();
    editorComposite.setPinned(pinned);
    if (wasPinned != pinned && ApplicationManager.getApplication().isDispatchThread()) {
      updateFileIconDecoration(file);
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
        EditorWithProviderComposite composite = findFileComposite(file);
        if (composite != null && !myOwner.getManager().isChanged(composite)) {
          // we found non modified file
          closingOrder.add(file);
        }
      }

      // Search in tabbed pane
      for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
        VirtualFile file = getFileAt(i);
        if (!myOwner.getManager().isChanged(getEditorAt(i))) {
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

    EditorWithProviderComposite composite = findFileComposite(file);
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

  private void defaultCloseFile(@NotNull VirtualFile file, boolean transferFocus) {
    closeFile(file, true, transferFocus);
  }

  private boolean fileCanBeClosed(@NotNull VirtualFile file, @Nullable VirtualFile fileToIgnore) {
    return isFileOpen(file) && !file.equals(fileToIgnore) && !isFilePinned(file);
  }

  @NotNull VirtualFile getFileAt(int i) {
    return getEditorAt(i).getFile();
  }

  @Override
  public String toString() {
    return "EditorWindow: files=" + Arrays.asList(getFiles());
  }
}
