// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.notebook.editor.BackedVirtualFile;
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
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.util.IconUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBRectangle;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

/**
 * Author: msk
 */
public class EditorWindow {
  private static final Logger LOG = Logger.getInstance(EditorWindow.class);

  public static final DataKey<EditorWindow> DATA_KEY = DataKey.create("editorWindow");

  protected JPanel myPanel;
  @NotNull private final EditorTabbedContainer myTabbedPane;
  private final EditorsSplitters myOwner;

  private boolean myIsDisposed;
  public static final Key<Integer> INITIAL_INDEX_KEY = Key.create("initial editor index");
  private final Stack<Pair<String, FileEditorOpenOptions>> myRemovedTabs = new Stack<Pair<String, FileEditorOpenOptions>>() {
    @Override
    public void push(Pair<String, FileEditorOpenOptions> pair) {
      if (size() >= UISettings.getInstance().getEditorTabLimit()) {
        remove(0);
      }
      super.push(pair);
    }
  };

  protected EditorWindow(@NotNull EditorsSplitters owner) {
    myOwner = owner;
    myPanel = new JPanel(new BorderLayout());
    myPanel.setOpaque(false);
    myPanel.setFocusable(false);

    myTabbedPane = new EditorTabbedContainer(this, getManager().getProject(), myOwner);
    myPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);

    // Tab layout policy
    if (UISettings.getInstance().getScrollTabLayoutInEditor()) {
      setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    } else {
      setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
    }

    myOwner.addWindow(this);
    if (myOwner.getCurrentWindow() == null) {
      myOwner.setCurrentWindow(this, false);
    }
    ApplicationManager.getApplication().getMessageBus().connect(myOwner).subscribe(UISettingsListener.TOPIC,
                                                                                   uiSettings -> updateTabsVisibility());
    updateTabsVisibility();
  }

  private void updateTabsVisibility() {
    myTabbedPane.getTabs().getPresentation().setHideTabs(UISettings.getInstance().getEditorTabPlacement() == UISettings.TABS_NONE || UISettings.getInstance().getPresentationMode());
  }

  public boolean isShowing() {
    return myPanel.isShowing();
  }

  public void closeAllExcept(final VirtualFile selectedFile) {
    getManager().runBulkTabChange(myOwner, splitters -> {
      final VirtualFile[] files = getFiles();
      for (final VirtualFile file : files) {
        if (!Comparing.equal(file, selectedFile) && !isFilePinned(file)) {
          closeFile(file);
        }
      }
    });
  }

  void dispose() {
    try {
      Disposer.dispose(myTabbedPane);
      myPanel.removeAll();
      myPanel.revalidate();
      myOwner.removeWindow(this);
    }
    finally {
      myIsDisposed = true;
    }
  }

  public boolean isDisposed() {
    return myIsDisposed;
  }

  public void closeFile(final VirtualFile file) {
    closeFile(file, true);
  }

  public void closeFile(final VirtualFile file, final boolean disposeIfNeeded) {
    closeFile(file, disposeIfNeeded, true);
  }

  boolean hasClosedTabs() {
    return !myRemovedTabs.empty();
  }

  void restoreClosedTab() {
    assert hasClosedTabs() : "Nothing to restore";

    final Pair<String, FileEditorOpenOptions> info = myRemovedTabs.pop();
    final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(info.getFirst());
    if (file != null) {
      getManager().openFileImpl4(this, file, null,
                                 new FileEditorOpenOptions()
                                   .withPin(info.getSecond().getPin())
                                   .withCurrentTab(true)
                                   .withFocusEditor(true)
                                   .withIndex(info.getSecond().getIndex()));
    }
  }

  public void closeFile(@NotNull final VirtualFile file, final boolean disposeIfNeeded, final boolean transferFocus) {
    final FileEditorManagerImpl editorManager = getManager();
    editorManager.runBulkTabChange(myOwner, splitters -> {
      final List<EditorWithProviderComposite> editors = splitters.findEditorComposites(file);
      if (editors.isEmpty()) return;
      try {
        final EditorWithProviderComposite editor = findFileComposite(file);

        final FileEditorManagerListener.Before beforePublisher =
          editorManager.getProject().getMessageBus().syncPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER);

        beforePublisher.beforeFileClosed(editorManager, file);

        if (editor != null) {
          final int componentIndex = findComponentIndex(editor.getComponent());
          if (componentIndex >= 0) { // editor could close itself on decomposition
            final int indexToSelect = calcIndexToSelect(file, componentIndex);
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
          final Project project = editorManager.getProject();
          if (!project.isDisposed()) {
            final FileEditorManagerListener afterPublisher =
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

  int calcIndexToSelect(VirtualFile fileBeingClosed, final int fileIndex) {
    final int currentlySelectedIndex = myTabbedPane.getSelectedIndex();
    if (currentlySelectedIndex != fileIndex) {
      // if the file being closed is not currently selected, keep the currently selected file open
      return currentlySelectedIndex;
    }
    UISettings uiSettings = UISettings.getInstance();
    if (uiSettings.getState().getActiveMruEditorOnClose()) {
      // try to open last visited file
      final List<VirtualFile> histFiles = EditorHistoryManager.getInstance(getManager ().getProject()).getFileList();
      for (int idx = histFiles.size() - 1; idx >= 0; idx--) {
        final VirtualFile histFile = histFiles.get(idx);
        if (histFile.equals(fileBeingClosed)) {
          continue;
        }
        final EditorWithProviderComposite editor = findFileComposite(histFile);
        if (editor == null) {
          continue; // ????
        }
        final int histFileIndex = findComponentIndex(editor.getComponent());
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

  public FileEditorManagerImpl getManager() { return myOwner.getManager(); }

  public int getTabCount() {
    return myTabbedPane.getTabCount();
  }

  void setForegroundAt(final int index, final Color color) {
    myTabbedPane.setForegroundAt(index, color);
  }

  void setWaveColor(final int index, @Nullable final Color color) {
    myTabbedPane.setWaveColor(index, color);
  }

  private void setIconAt(final int index, final Icon icon) {
    myTabbedPane.setIconAt(index, icon);
  }

  private void setTitleAt(final int index, final String text) {
    myTabbedPane.setTitleAt(index, text);
  }

  private void setBackgroundColorAt(final int index, final Color color) {
    myTabbedPane.setBackgroundColorAt(index, color);
  }

  private void setToolTipTextAt(final int index, final String text) {
    myTabbedPane.setToolTipTextAt(index, text);
  }


  void setTabLayoutPolicy(final int policy) {
    myTabbedPane.setTabLayoutPolicy(policy);
  }

  void setTabsPlacement(final int tabPlacement) {
    myTabbedPane.setTabPlacement(tabPlacement);
  }

  public void setAsCurrentWindow(final boolean requestFocus) {
    myOwner.setCurrentWindow(this, requestFocus);
  }

  void updateFileBackgroundColor(@NotNull VirtualFile file) {
    final int index = findEditorIndex(findFileComposite(file));
    if (index != -1) {
      final Color color = EditorTabPresentationUtil.getEditorTabBackgroundColor(getManager().getProject(), file, this);
      setBackgroundColorAt(index, color);
    }
  }

  public EditorsSplitters getOwner() {
    return myOwner;
  }

  boolean isEmptyVisible() {
    return myTabbedPane.isEmptyVisible();
  }

  public Dimension getSize() {
    return myPanel.getSize();
  }

  @NotNull
  public EditorTabbedContainer getTabbedPane() {
    return myTabbedPane;
  }

  public void requestFocus(boolean forced) {
    myTabbedPane.requestFocus(forced);
  }

  public boolean isValid() {
    return myPanel.isShowing();
  }

  protected static class TComp extends JPanel implements DataProvider, EditorWindowHolder {
    @NotNull final EditorWithProviderComposite myEditor;
    protected final EditorWindow myWindow;

    /*@Override
    public void addNotify() {
      super.addNotify();
      requestFocusInWindow();
    }*/

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
            final JComponent focus = myEditor.getSelectedEditorWithProvider().getFirst().getPreferredFocusedComponent();
            if (focus != null && !focus.hasFocus()) {
              getGlobalInstance().requestFocus(focus, true);
            }
          });
        }
      });
      setFocusTraversalPolicy(new FocusTraversalPolicy() {
        @Override
        public Component getComponentAfter(Container aContainer, Component aComponent) {
          return myEditor.getComponent();
        }

        @Override
        public Component getComponentBefore(Container aContainer, Component aComponent) {
          return myEditor.getComponent();
        }

        @Override
        public Component getFirstComponent(Container aContainer) {
          return myEditor.getComponent();
        }

        @Override
        public Component getLastComponent(Container aContainer) {
          return myEditor.getComponent();
        }

        @Override
        public Component getDefaultComponent(Container aContainer) {
          return myEditor.getComponent();
        }
      });
    }

    @NotNull
    @Override
    public EditorWindow getEditorWindow() {
      return myWindow;
    }

    @Override
    public Object getData(@NotNull String dataId) {
      if (CommonDataKeys.VIRTUAL_FILE.is(dataId)){
        final VirtualFile virtualFile = myEditor.getFile();
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

  public EditorWithProviderComposite getSelectedEditor() {
    return getSelectedEditor(false);
  }

  /**
   * @param ignorePopup if <code>false</code> and context menu is shown currently for some tab,
   *                    editor for which menu is invoked will be returned
   */
  public EditorWithProviderComposite getSelectedEditor(boolean ignorePopup) {
    final TComp comp = ObjectUtils.tryCast(myTabbedPane.getSelectedComponent(ignorePopup), TComp.class);
    if (comp != null) {
      return comp.myEditor;
    }
    return null;
  }

  public EditorWithProviderComposite[] getEditors() {
    final int tabCount = getTabCount();
    final EditorWithProviderComposite[] res = new EditorWithProviderComposite[tabCount];
    for (int i = 0; i != tabCount; ++i) {
      res[i] = getEditorAt(i);
    }
    return res;
  }

  public VirtualFile[] getFiles() {
    final int tabCount = getTabCount();
    final VirtualFile[] res = new VirtualFile[tabCount];
    for (int i = 0; i != tabCount; ++i) {
      res[i] = getEditorAt(i).getFile();
    }
    return res;
  }

  public void setSelectedEditor(final EditorComposite editor, final boolean focusEditor) {
    if (editor == null) return; // nothing to select or to focus
    // select an editor in a tabbed pane and then focus an editor if needed
    final int index = findFileIndex(editor.getFile());
    if (index != -1) {
      UIUtil.invokeLaterIfNeeded(() -> {
        if (!isDisposed()) {
          myTabbedPane.setSelectedIndex(index, focusEditor);
        }
      });
    }
  }

  public void setEditor(@Nullable final EditorWithProviderComposite editor, final boolean focusEditor) {
    setEditor(editor, true, focusEditor);
  }

  public void setEditor(@Nullable final EditorWithProviderComposite editor, final boolean selectEditor, final boolean focusEditor) {
    if (editor != null) {
      onBeforeSetEditor(editor.getFile());

      final int index = findEditorIndex(editor);
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

        final VirtualFile file = editor.getFile();
        final Icon template = AllIcons.FileTypes.Text;
        EmptyIcon emptyIcon = EmptyIcon.create(template.getIconWidth(), template.getIconHeight());
        myTabbedPane.insertTab(file, emptyIcon, new TComp(this, editor), null, indexToInsert, editor);
        trimToSize(UISettings.getInstance().getEditorTabLimit(), file, false);
        if (selectEditor) {
          setSelectedEditor(editor, focusEditor);
        }
        myOwner.updateFileIconImmediately(file);
        myOwner.updateFileColor(file);
      }
      myOwner.setCurrentWindow(this, false);
    }
    if (!myOwner.isInsideChange()) myOwner.validate();
  }

  protected void onBeforeSetEditor(VirtualFile file) {
  }

  private boolean splitAvailable() {
    return getTabCount() >= 1;
  }

  @Nullable
  public EditorWindow split(final int orientation, boolean forceSplit, @Nullable VirtualFile virtualFile, boolean focusNew) {
    checkConsistency();
    final FileEditorManagerImpl fileEditorManager = myOwner.getManager();
    if (splitAvailable()) {
      if (!forceSplit && inSplitter()) {
        final EditorWindow[] siblings = findSiblings();
        final EditorWindow target = siblings[0];
        if (virtualFile != null) {
          final FileEditor[] editors = fileEditorManager.openFileImpl3(target, virtualFile, focusNew, null).first;
          syncCaretIfPossible(editors);
        }
        return target;
      }
      final JPanel panel = myPanel;
      panel.setBorder(null);
      final int tabCount = getTabCount();
      if (tabCount != 0) {
        myPanel = new JPanel(new BorderLayout());
        myPanel.setOpaque(false);

        final Splitter splitter = new OnePixelSplitter(orientation == JSplitPane.VERTICAL_SPLIT, 0.5f, 0.1f, 0.9f);
        final EditorWindow res = new EditorWindow(myOwner);
        final EditorWithProviderComposite selectedEditor = getSelectedEditor();
        panel.remove(myTabbedPane.getComponent());
        panel.add(splitter, BorderLayout.CENTER);
        splitter.setFirstComponent(myPanel);
        myPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);
        splitter.setSecondComponent(res.myPanel);
          /*
          for (int i = 0; i != tabCount; ++i) {
            final EditorWithProviderComposite eC = getEditorAt(i);
            final VirtualFile file = eC.getFile();
            fileEditorManager.openFileImpl3(res, file, false, null);
            res.setFilePinned (file, isFilePinned (file));
          }
          */
        // open only selected file in the new splitter instead of opening all tabs
        final VirtualFile file = selectedEditor.getFile();

        if (virtualFile == null) {
          for (FileEditorAssociateFinder finder : FileEditorAssociateFinder.EP_NAME.getExtensionList()) {
            VirtualFile associatedFile = finder.getAssociatedFileToOpen(fileEditorManager.getProject(), file);

            if (associatedFile != null) {
              virtualFile = associatedFile;
              break;
            }
          }
        }

        final VirtualFile nextFile = virtualFile == null ? file : virtualFile;
        HistoryEntry currentState = selectedEditor.currentStateAsHistoryEntry();
        final FileEditor[] editors = fileEditorManager.openFileImpl4(res, nextFile, currentState,
                                                                     new FileEditorOpenOptions()
                                                                       .withCurrentTab(true)
                                                                       .withFocusEditor(focusNew)
                                                                       .withExactState()).first;
        syncCaretIfPossible(editors);
        res.setFilePinned(nextFile, isFilePinned(file));
        if (!focusNew) {
          res.setSelectedEditor(selectedEditor, true);
          getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(selectedEditor.getComponent(), true));
        }
        panel.revalidate();
        return res;
      }
    }
    return null;
  }

  /**
   * Tries to setup caret and viewport for the given editor from the selected one.
   *
   * @param toSync    editor to setup caret and viewport for
   */
  private void syncCaretIfPossible(@Nullable FileEditor[] toSync) {
    if (toSync == null) {
      return;
    }

    final EditorWithProviderComposite from = getSelectedEditor();
    if (from == null) {
      return;
    }

    final FileEditor caretSource = from.getSelectedEditor();
    if (!(caretSource instanceof TextEditor)) {
      return;
    }

    final Editor editorFrom = ((TextEditor)caretSource).getEditor();
    final int offset = editorFrom.getCaretModel().getOffset();
    if (offset <= 0) {
      return;
    }

    final int scrollOffset = editorFrom.getScrollingModel().getVerticalScrollOffset();

    for (FileEditor fileEditor : toSync) {
      if (!(fileEditor instanceof TextEditor)) {
        continue;
      }
      final Editor editor = ((TextEditor)fileEditor).getEditor();
      if (editorFrom.getDocument() == editor.getDocument()) {
        editor.getCaretModel().moveToOffset(offset);
        final ScrollingModel scrollingModel = editor.getScrollingModel();
        scrollingModel.scrollVertically(scrollOffset);

        SwingUtilities.invokeLater(() -> {
          if (!editor.isDisposed()) {
            scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE);
          }
        });
      }
    }
  }

  public EditorWindow[] findSiblings() {
    checkConsistency();
    final ArrayList<EditorWindow> res = new ArrayList<>();
    if (myPanel.getParent() instanceof Splitter) {
      final Splitter splitter = (Splitter)myPanel.getParent();
      for (final EditorWindow win : myOwner.getWindows()) {
        if (win != this && SwingUtilities.isDescendingFrom(win.myPanel, splitter)) {
          res.add(win);
        }
      }
    }
    return res.toArray(new EditorWindow[0]);
  }

  void changeOrientation() {
    checkConsistency();
    final Container parent = myPanel.getParent();
    if (parent instanceof Splitter) {
      final Splitter splitter = (Splitter)parent;
      splitter.setOrientation(!splitter.getOrientation());
    }
  }

  void updateFileIcon(VirtualFile file) {
    final int index = findEditorIndex(findFileComposite(file));
    LOG.assertTrue(index != -1);
    setIconAt(index, getFileIcon(file));
  }

  void updateFileName(VirtualFile file) {
    final int index = findEditorIndex(findFileComposite(file));
    if (index != -1) {
      setTitleAt(index, EditorTabPresentationUtil.getEditorTabTitle(getManager().getProject(), file, this));
      setToolTipTextAt(index, UISettings.getInstance().getShowTabsTooltips()
                              ? getManager().getFileTooltipText(file)
                              : null);
    }
  }

  /**
   * @return icon which represents file's type and modification status
   */
  private Icon getFileIcon(@NotNull final VirtualFile file) {
    if (!file.isValid()) {
      Icon fakeIcon = FileTypes.UNKNOWN.getIcon();
      assert fakeIcon != null : "Can't find the icon for unknown file type";
      return fakeIcon;
    }

    final Icon baseIcon = IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, getManager().getProject());

    int count = 1;

    final Icon pinIcon;
    final EditorComposite composite = findFileComposite(file);
    if (composite != null && composite.isPinned()) {
      count++;
      pinIcon = AllIcons.Nodes.TabPin;
    }
    else {
      pinIcon = null;
    }

    final Icon modifiedIcon;
    UISettings settings = UISettings.getInstance();
    if (settings.getMarkModifiedTabsWithAsterisk()) {
      Icon crop = IconUtil.cropIcon(AllIcons.General.Modified, new JBRectangle(3, 3, 7, 7));
      modifiedIcon = settings.getMarkModifiedTabsWithAsterisk() && composite != null && composite.isModified() ? crop : new EmptyIcon(7, 7);
      count++;
    }
    else {
      modifiedIcon = null;
    }

    if (count == 1) return baseIcon;

    int i = 0;
    final LayeredIcon result = new LayeredIcon(count);
    result.setIcon(baseIcon, i++);
    if (pinIcon != null) result.setIcon(pinIcon, i++);
    if (modifiedIcon != null) result.setIcon(modifiedIcon, i++, -modifiedIcon.getIconWidth() / 2, 0);

    return JBUI.scale(result);
  }

  public void unsplit(boolean setCurrent) {
    checkConsistency();
    final Container splitter = myPanel.getParent();

    if (!(splitter instanceof Splitter)) return;

    EditorWithProviderComposite editorToSelect = getSelectedEditor();
    final EditorWindow[] siblings = findSiblings();
    final JPanel parent = (JPanel)splitter.getParent();

    for (EditorWindow eachSibling : siblings) {
      // selected editors will be added first
      final EditorWithProviderComposite selected = eachSibling.getSelectedEditor();
      if (editorToSelect == null) {
        editorToSelect = selected;
      }
    }

    for (final EditorWindow sibling : siblings) {
      final EditorWithProviderComposite[] siblingEditors = sibling.getEditors();
      for (final EditorWithProviderComposite siblingEditor : siblingEditors) {
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

  private void processSiblingEditor(final EditorWithProviderComposite siblingEditor) {
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
    final EditorWithProviderComposite editor = getSelectedEditor();
    return editor == null ? null : editor.getFile();
  }

  @Nullable
  public EditorWithProviderComposite findFileComposite(VirtualFile file) {
    if (file instanceof BackedVirtualFile)
      file = ((BackedVirtualFile)file).getOriginFile();

    for (int i = 0; i != getTabCount(); ++i) {
      final EditorWithProviderComposite editor = getEditorAt(i);
      if (editor.getFile().equals(file)) {
        return editor;
      }
    }
    return null;
  }


  private int findComponentIndex(final Component component) {
    for (int i = 0; i != getTabCount(); ++i) {
      final EditorWithProviderComposite editor = getEditorAt(i);
      if (editor.getComponent ().equals (component)) {
        return i;
      }
    }
    return -1;
  }

  int findEditorIndex(final EditorComposite editorToFind) {
    for (int i = 0; i != getTabCount(); ++i) {
      final EditorWithProviderComposite editor = getEditorAt(i);
      if (editor.equals (editorToFind)) {
        return i;
      }
    }
    return -1;
  }

  int findFileIndex(final VirtualFile fileToFind) {
    for (int i = 0; i != getTabCount(); ++i) {
      final VirtualFile file = getFileAt(i);
      if (file.equals (fileToFind)) {
        return i;
      }
    }
    return -1;
  }

  private EditorWithProviderComposite getEditorAt(final int i) {
    return ((TComp)myTabbedPane.getComponentAt(i)).myEditor;
  }

  public boolean isFileOpen(final VirtualFile file) {
    return findFileComposite(file) != null;
  }

  public boolean isFilePinned(final VirtualFile file) {
    final EditorComposite editorComposite = findFileComposite(file);
    if (editorComposite == null) {
      throw new IllegalArgumentException("file is not open: " + file.getPath());
    }
    return editorComposite.isPinned();
  }

  public void setFilePinned(final VirtualFile file, final boolean pinned) {
    final EditorComposite editorComposite = findFileComposite(file);
    if (editorComposite == null) {
      throw new IllegalArgumentException("file is not open: " + file.getPath());
    }
    boolean wasPinned = editorComposite.isPinned();
    editorComposite.setPinned(pinned);
    if (wasPinned != pinned && ApplicationManager.getApplication().isDispatchThread()) {
      updateFileIcon(file);
    }
  }

  void trimToSize(final int limit, @Nullable final VirtualFile fileToIgnore, final boolean transferFocus) {
    getManager().getReady(this).doWhenDone(() -> {
      if (!isDisposed()) {
        doTrimSize(limit, fileToIgnore, UISettings.getInstance().getState().getCloseNonModifiedFilesFirst(), transferFocus);
      }
    });
  }

  private void doTrimSize(int limit, @Nullable VirtualFile fileToIgnore, boolean closeNonModifiedFilesFirst, boolean transferFocus) {
    LinkedHashSet<VirtualFile> closingOrder = getTabClosingOrder(closeNonModifiedFilesFirst);
    VirtualFile selectedFile = getSelectedFile();
    if (shouldCloseSelected(fileToIgnore)) {
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

  private LinkedHashSet<VirtualFile> getTabClosingOrder(boolean closeNonModifiedFilesFirst) {
    final VirtualFile[] allFiles = getFiles();
    final List<VirtualFile> histFiles = EditorHistoryManager.getInstance(getManager().getProject()).getFileList();

    LinkedHashSet<VirtualFile> closingOrder = new LinkedHashSet<>();

    // first, we search for files not in history
    for (final VirtualFile file : allFiles) {
      if (!histFiles.contains(file)) {
        closingOrder.add(file);
      }
    }

    if (closeNonModifiedFilesFirst) {
      // Search in history
      for (final VirtualFile file : histFiles) {
        EditorWithProviderComposite composite = findFileComposite(file);
        if (composite != null && !myOwner.getManager().isChanged(composite)) {
          // we found non modified file
          closingOrder.add(file);
        }
      }

      // Search in tabbed pane
      for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
        final VirtualFile file = getFileAt(i);
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

    final VirtualFile selectedFile = getSelectedFile();
    closingOrder.remove(selectedFile);
    closingOrder.add(selectedFile); // selected should be closed last
    return closingOrder;
  }

  private boolean shouldCloseSelected(VirtualFile fileToIgnore) {
    if (!UISettings.getInstance().getReuseNotModifiedTabs() || !myOwner.getManager().getProject().isInitialized()) {
      return false;
    }

    VirtualFile file = getSelectedFile();
    if (file == null || !isFileOpen(file) || isFilePinned(file)) {
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

  private boolean areAllTabsPinned(VirtualFile fileToIgnore) {
    for (int i = myTabbedPane.getTabCount() - 1; i >= 0; i--) {
      if (fileCanBeClosed(getFileAt(i), fileToIgnore)) {
        return false;
      }
    }
    return true;
  }

  private void defaultCloseFile(VirtualFile file, boolean transferFocus) {
    closeFile(file, true, transferFocus);
  }

  private boolean fileCanBeClosed(final VirtualFile file, @Nullable final VirtualFile fileToIgnore) {
    return isFileOpen (file) && !file.equals(fileToIgnore) && !isFilePinned(file);
  }

  VirtualFile getFileAt(int i) {
    return getEditorAt(i).getFile();
  }

  @Override
  public String toString() {
    return "EditorWindow: files=" + Arrays.asList(getFiles());
  }
}
