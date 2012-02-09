/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Author: msk
 */
public class EditorWindow {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.EditorWindow");

  public static final DataKey<EditorWindow> DATA_KEY = DataKey.create("editorWindow");

  protected JPanel myPanel;
  private EditorTabbedContainer myTabbedPane;
  private final EditorsSplitters myOwner;
  private static final Icon MODIFIED_ICON = IconLoader.getIcon("/general/modified.png");
  private static final Icon GAP_ICON = new EmptyIcon(MODIFIED_ICON.getIconWidth(), MODIFIED_ICON.getIconHeight());

  private boolean myIsDisposed = false;
  private static final Icon PIN_ICON = IconLoader.getIcon("/nodes/tabPin.png");
  public static final Key<Integer> INITIAL_INDEX_KEY = Key.create("initial editor index");

  protected EditorWindow(final EditorsSplitters owner) {
    myOwner = owner;
    myPanel = new JPanel(new BorderLayout());
    myPanel.setBorder(new AdaptiveBorder());
    myPanel.setOpaque(false);

    myTabbedPane = null;

    final int tabPlacement = UISettings.getInstance().EDITOR_TAB_PLACEMENT;
    if (tabPlacement != UISettings.TABS_NONE) {
      createTabs(tabPlacement);
    }

    // Tab layout policy
    if (UISettings.getInstance().SCROLL_TAB_LAYOUT_IN_EDITOR) {
      setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    } else {
      setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
    }

    getWindows().add(this);
    if (myOwner.getCurrentWindow() == null) {
      myOwner.setCurrentWindow(this, false);
    }
  }

  private void createTabs(int tabPlacement) {
    LOG.assertTrue (myTabbedPane == null);
    myTabbedPane = new EditorTabbedContainer(this, getManager().getProject(), tabPlacement);
    myPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);
  }

  public boolean isShowing() {
    return myPanel.isShowing();
  }

  public void closeAllExcept(final VirtualFile selectedFile) {
    final VirtualFile[] files = getFiles();
    for (final VirtualFile file : files) {
      if (file != selectedFile && !isFilePinned(file)) {
        closeFile(file);
      }
    }
  }

  private static class AdaptiveBorder implements Border {
    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      Insets insets = ((JComponent)c).getInsets();
      g.setColor(UIUtil.getPanelBackground());
      paintBorder(g, x, y, width, height, insets);
      g.setColor(new Color(0, 0, 0, 90));
      paintBorder(g, x, y, width, height, insets);
    }

    private static void paintBorder(Graphics g, int x, int y, int width, int height, Insets insets) {
      if (insets.left == 1) {
        g.drawLine(x, y, x, y + height);
      }

      if (insets.right == 1) {
        g.drawLine(x + width - 1, y, x + width - 1, y + height);
      }

      if (insets.bottom == 1) {
        g.drawLine(x, y + height - 1, x + width, y + height - 1);
      }
    }

    @Override
    public Insets getBorderInsets(Component c) {
      Container parent = c.getParent();
      if (parent instanceof Splitter) {
        boolean editorToTheLeft = false;
        boolean editorToTheRight = false;
        boolean editorToTheDown = false;

        Splitter splitter = (Splitter)parent;
        
        boolean vertical = splitter.getOrientation();
        if (vertical && splitter.getFirstComponent() == c) {
          editorToTheDown = true;
        } else if (!vertical) {
          if (splitter.getFirstComponent() == c) {
            editorToTheRight = true;
          }
          if (splitter.getSecondComponent() == c) editorToTheLeft = true;
        }

        
        //Frame frame = (Frame) SwingUtilities.getAncestorOfClass(Frame.class, c);
        //if (frame instanceof IdeFrame) {
        //  Project project = ((IdeFrame)frame).getProject();
        //  ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
        //  if (!editorToTheLeft) {
        //    List<String> left = toolWindowManager.getIdsOn(ToolWindowAnchor.LEFT);
        //    if (left.size() > 0) {
        //      for (String lid : left) {
        //        ToolWindow window = toolWindowManager.getToolWindow(lid);
        //        editorToTheLeft = window != null && window.isVisible() && window.getType() == ToolWindowType.DOCKED;
        //        if (editorToTheLeft) break;
        //      }
        //    }
        //  }
        //
        //  if (!editorToTheRight) {
        //    List<String> right = toolWindowManager.getIdsOn(ToolWindowAnchor.RIGHT);
        //    if (right.size() > 0) {
        //      for (String lid : right) {
        //        ToolWindow window = toolWindowManager.getToolWindow(lid);
        //        editorToTheRight = window != null && window.isVisible() && window.getType() == ToolWindowType.DOCKED;
        //        if (editorToTheRight) break;
        //      }
        //    }
        //  }
        //}

        Splitter outer = nextOuterSplitter(splitter);
        if (outer != null) {
          boolean outerVertical = outer.getOrientation();
          if (!outerVertical) {
            if (splitter.getParent() == outer.getFirstComponent()) editorToTheRight = true;
            if (splitter.getParent() == outer.getSecondComponent()) editorToTheLeft = true;
          } else {
            if (splitter.getParent() == outer.getFirstComponent()) {
              editorToTheDown = true;
            }
          }
        }

        int left = editorToTheLeft ? 1 : 0;
        int right = editorToTheRight ? 1 : 0;
        int bottom = editorToTheDown ? 1 : 0;
        return new Insets(0, left, bottom, right);
      }
      
      return new Insets(0, 0, 0, 0);
    }
    
    @Nullable
    private static Splitter nextOuterSplitter(Component c) {
      Container parent = c.getParent();
      if (parent == null) return null;
      Container grandParent = parent.getParent();
      if (grandParent instanceof Splitter) return (Splitter)grandParent;
      return null;
    }

    @Override
    public boolean isBorderOpaque() {
      return true;
    }
  }

  private Set<EditorWindow> getWindows() {
    return myOwner.myWindows;
  }

  private void dispose() {
    try {
      disposeTabs();
      getWindows ().remove(this);
    }
    finally {
      myIsDisposed = true;
    }
  }

  public boolean isDisposed() {
    return myIsDisposed;
  }

  private void disposeTabs() {
    if (myTabbedPane != null) {
      Disposer.dispose(myTabbedPane);
      myTabbedPane = null;
    }
    myPanel.removeAll();
    myPanel.revalidate();
  }

  public void closeFile(final VirtualFile file) {
    closeFile (file, true);
  }

  public void closeFile(final VirtualFile file, final boolean unsplit) {
    closeFile(file, unsplit, true);
  }

  public void closeFile(final VirtualFile file, final boolean unsplit, final boolean transferFocus) {
    final FileEditorManagerImpl editorManager = getManager();
    editorManager.runChange(new FileEditorManagerChange() {
      public void run(EditorsSplitters splitters) {
        final List<EditorWithProviderComposite> editors = splitters.findEditorComposites(file);
        if (editors.isEmpty()) return;
        try {
          final EditorWithProviderComposite editor = findFileComposite(file);

          final FileEditorManagerListener.Before beforePublisher =
            editorManager.getProject().getMessageBus().syncPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER);

          beforePublisher.beforeFileClosed(editorManager, file);

          if (myTabbedPane != null && editor != null) {
            final int componentIndex = findComponentIndex(editor.getComponent());
            if (componentIndex >= 0) { // editor could close itself on decomposition
              final int indexToSelect = calcIndexToSelect(file, componentIndex);
              final ActionCallback removeTab = myTabbedPane.removeTabAt(componentIndex, indexToSelect, transferFocus);
              final Runnable disposer = new Runnable() {
                public void run() {
                  editorManager.disposeComposite(editor);
                }
              };
              disposer.run();
              //removeTab.doWhenDone(disposer);
            }
          }
          else {
            myPanel.removeAll ();
            if (editor != null) {
              editorManager.disposeComposite(editor);
            }
          }

          if (unsplit && getTabCount() == 0) {
            unsplit (true);
          }
          myPanel.revalidate ();
          if (myTabbedPane == null) {
            // in tabless mode
            myPanel.repaint();
          }
        }
        finally {
          editorManager.removeSelectionRecord(file, EditorWindow.this);

          editorManager.notifyPublisher(new Runnable() {
            @Override
            public void run() {
              final Project project = editorManager.getProject();
              if (!project.isDisposed()) {
                final FileEditorManagerListener afterPublisher =
                  project.getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER);
                afterPublisher.fileClosed(editorManager, file);
              }
            }
          });

          splitters.afterFileClosed(file);
        }
      }
    }, myOwner);
  }

  private int calcIndexToSelect(VirtualFile fileBeingClosed, final int fileIndex) {
    final int currentlySelectedIndex = myTabbedPane.getSelectedIndex();
    if (currentlySelectedIndex != fileIndex) {
      // if the file being closed is not currently selected, keep the currently selected file open
      return currentlySelectedIndex;
    }
    UISettings uiSettings = UISettings.getInstance();
    if (uiSettings.ACTIVATE_MRU_EDITOR_ON_CLOSE) {
      // try to open last visited file
      final VirtualFile[] histFiles = EditorHistoryManager.getInstance(getManager ().getProject()).getFiles();
      for (int idx = histFiles.length - 1; idx >= 0; idx--) {
        final VirtualFile histFile = histFiles[idx];
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
    if (uiSettings.ACTIVATE_RIGHT_EDITOR_ON_CLOSE && (fileIndex + 1 < myTabbedPane.getTabCount())) {
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
    if (myTabbedPane != null) {
      return myTabbedPane.getTabCount();
    }
    return myPanel.getComponentCount();
  }

  public void setForegroundAt(final int index, final Color color) {
    if (myTabbedPane != null) {
      myTabbedPane.setForegroundAt(index, color);
    }
  }

  public void setWaveColor(final int index, @Nullable final Color color) {
    if (myTabbedPane != null) {
      myTabbedPane.setWaveColor(index, color);
    }
  }

  private void setIconAt(final int index, final Icon icon) {
    if (myTabbedPane != null) {
      myTabbedPane.setIconAt(index, icon);
    }
  }

  private void setTitleAt(final int index, final String text) {
    if (myTabbedPane != null) {
      myTabbedPane.setTitleAt(index, text);
    }
  }

  private void setBackgroundColorAt(final int index, final Color color) {
    if (myTabbedPane != null) {
      myTabbedPane.setBackgroundColorAt(index, color);
    }
  }

  private void setToolTipTextAt(final int index, final String text) {
    if (myTabbedPane != null) {
      myTabbedPane.setToolTipTextAt(index, text);
    }
  }


  public void setTabLayoutPolicy(final int policy) {
    if (myTabbedPane != null) {
      myTabbedPane.setTabLayoutPolicy(policy);
    }
  }

  public void setTabsPlacement(final int tabPlacement) {
    if (tabPlacement != UISettings.TABS_NONE) {
      if (myTabbedPane == null) {
        final EditorWithProviderComposite editor = getSelectedEditor();
        myPanel.removeAll();
        createTabs(tabPlacement);
        setEditor (editor, true);
      }
      else {
        myTabbedPane.setTabPlacement(tabPlacement);
      }
    }
    else if (myTabbedPane != null) {
      final boolean focusEditor = ToolWindowManager.getInstance(getManager().getProject()).isEditorComponentActive();
      final VirtualFile currentFile = getSelectedFile();
      final VirtualFile[] files = getFiles();
      for (VirtualFile file : files) {
        closeFile(file, false);
      }
      disposeTabs();
      if (currentFile != null) {
        getManager().openFileImpl2(this, currentFile, focusEditor && myOwner.getCurrentWindow() == this);
      }
      else {
        myPanel.repaint();
      }
    }
  }

  public void setAsCurrentWindow(final boolean requestFocus) {
    myOwner.setCurrentWindow(this, requestFocus);
  }

  public void updateFileBackgroundColor(final VirtualFile file) {
    final int index = findEditorIndex(findFileComposite(file));
    if (index != -1) {
      final Color color = EditorTabbedContainer.calcTabColor(getManager().getProject(), file);
      setBackgroundColorAt(index, color);
    }
  }

  public EditorsSplitters getOwner() {
    return myOwner;
  }

  public boolean isEmptyVisible() {
    return myTabbedPane != null ? myTabbedPane.isEmptyVisible() : getFiles().length == 0;
  }

  public Dimension getSize() {
    return myPanel.getSize();
  }

  public EditorTabbedContainer getTabbedPane() {
    return myTabbedPane;
  }

  public void requestFocus(boolean forced) {
    if (myTabbedPane != null) {
      myTabbedPane.requestFocus(forced);
    }
  }

  public boolean isValid() {
    return myPanel.isShowing();
  }

  public void setPaintBlocked(boolean blocked) {
    if (myTabbedPane != null) {
      myTabbedPane.setPaintBlocked(blocked);
    }
  }

  protected static class TComp extends JPanel implements DataProvider, EditorWindowHolder {
    final EditorWithProviderComposite myEditor;
    protected final EditorWindow myWindow;

    TComp(final EditorWindow window, final EditorWithProviderComposite editor) {
      super(new BorderLayout());
      myEditor = editor;
      myWindow = window;
      add(editor.getComponent(), BorderLayout.CENTER);
    }

    @Override
    public EditorWindow getEditorWindow() {
      return myWindow;
    }

    public Object getData(String dataId) {
      if (PlatformDataKeys.VIRTUAL_FILE.is(dataId)){
        final VirtualFile virtualFile = myEditor.getFile();
        return virtualFile.isValid() ? virtualFile : null;
      }
      else if (PlatformDataKeys.PROJECT.is(dataId)) {
        return myEditor.getFileEditorManager().getProject();
      }
      return null;
    }
  }

  protected static class TCompForTablessMode extends TComp{
    TCompForTablessMode(final EditorWindow window, final EditorWithProviderComposite editor) {
      super(window, editor);
    }

    public Object getData(String dataId) {
      if (EditorWindow.DATA_KEY.is(dataId)){
        // this is essintial for ability to close opened file
        return myWindow;
      }
      return super.getData(dataId);
    }
  }

  private void checkConsistency() {
    LOG.assertTrue(getWindows().contains(this), "EditorWindow not in collection");
  }

  public EditorWithProviderComposite getSelectedEditor() {
    final TComp comp;
    if (myTabbedPane != null) {
      comp = (TComp)myTabbedPane.getSelectedComponent();
    }
    else if (myPanel.getComponentCount() != 0) {
      final Component component = myPanel.getComponent(0);
      comp = component instanceof TComp ? (TComp)component : null;
    }
    else {
      return null;
    }

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
    if (myTabbedPane == null) {
      return;
    }
    if (editor != null) {
      final int index = findFileIndex(editor.getFile());
      if (index != -1) {
        myTabbedPane.setSelectedIndex(index, focusEditor);
      }
    }
  }

  public void setEditor(@Nullable final EditorWithProviderComposite editor, final boolean focusEditor) {
    if (editor != null) {
      if (myTabbedPane == null) {
        myPanel.removeAll ();
        myPanel.add (new TCompForTablessMode(this, editor), BorderLayout.CENTER);
        myPanel.revalidate ();
        return;
      }

      final int index = findEditorIndex(editor);
      if (index != -1) {
        setSelectedEditor(editor, focusEditor);
      }
      else {
        Integer initialIndex = editor.getFile().getUserData(INITIAL_INDEX_KEY);
        int indexToInsert = 0;
        if (initialIndex == null) {
          int selectedIndex = myTabbedPane.getSelectedIndex();
          if (selectedIndex >= 0) {
            indexToInsert = UISettings.getInstance().ACTIVATE_RIGHT_EDITOR_ON_CLOSE ? selectedIndex : selectedIndex + 1; 
          }
        } else {
          indexToInsert = initialIndex;
        }
        
        final VirtualFile file = editor.getFile();
        final Icon template = IconLoader.getIcon("/fileTypes/text.png");
        myTabbedPane.insertTab(file, new EmptyIcon(template.getIconWidth(), template.getIconHeight()), new TComp(this, editor), null, indexToInsert);
        trimToSize(UISettings.getInstance().EDITOR_TAB_LIMIT, file, false);
        setSelectedEditor(editor, focusEditor);
        myOwner.updateFileIcon(file);
        myOwner.updateFileColor(file);
      }
      myOwner.setCurrentWindow(this, false);
    }
    myPanel.revalidate();
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
          final FileEditor[] editors = fileEditorManager.openFileImpl3(target, virtualFile, focusNew, null, true).first;
          syncCaretIfPossible(editors);
        }
        return target;
      }
      final JPanel panel = myPanel;
      panel.setBorder(null);
      final int tabCount = getTabCount();
      if (tabCount != 0) {
        final EditorWithProviderComposite firstEC = getEditorAt(0);
        myPanel = new JPanel(new BorderLayout());
        myPanel.setOpaque(false);
        myPanel.setBorder(new AdaptiveBorder());
        myPanel.setOpaque(false);
        
        final Splitter splitter = new Splitter(orientation == JSplitPane.VERTICAL_SPLIT, 0.5f, 0.1f, 0.9f);
        final EditorWindow res = new EditorWindow(myOwner);
        if (myTabbedPane != null) {
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
          final VirtualFile nextFile = virtualFile == null ? file : virtualFile;
          final FileEditor[] editors = fileEditorManager.openFileImpl3(res, nextFile, false, null, true).first;
          syncCaretIfPossible(editors);
          res.setFilePinned (nextFile, isFilePinned (file));
          if (!focusNew) {
            res.setSelectedEditor(selectedEditor, true);
            selectedEditor.getComponent().requestFocus();
          }
          panel.revalidate();
        }
        else {
          panel.removeAll();
          panel.add(splitter, BorderLayout.CENTER);
          splitter.setFirstComponent(myPanel);
          splitter.setSecondComponent(res.myPanel);
          panel.revalidate();
          final VirtualFile firstFile = firstEC.getFile();
          final VirtualFile nextFile = virtualFile == null ? firstFile : virtualFile;
          final FileEditor[] firstEditors = fileEditorManager.openFileImpl3(this, firstFile, !focusNew, null, true).first;
          syncCaretIfPossible(firstEditors);
          final FileEditor[] secondEditors = fileEditorManager.openFileImpl3(res, nextFile, focusNew, null, true).first;
          syncCaretIfPossible(secondEditors);
        }
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

        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (!editor.isDisposed()) {
              scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE);
            }
          }
        });
      }
    }
  }

  public EditorWindow[] findSiblings() {
    checkConsistency();
    final ArrayList<EditorWindow> res = new ArrayList<EditorWindow>();
    if (myPanel.getParent() instanceof Splitter) {
      final Splitter splitter = (Splitter)myPanel.getParent();
      for (final EditorWindow win : getWindows()) {
        if (win != this && SwingUtilities.isDescendingFrom(win.myPanel, splitter)) {
          res.add(win);
        }
      }
    }
    return res.toArray(new EditorWindow[res.size()]);
  }

  public void changeOrientation() {
    checkConsistency();
    final Container parent = myPanel.getParent();
    if (parent instanceof Splitter) {
      final Splitter splitter = (Splitter)parent;
      splitter.setOrientation(!splitter.getOrientation());
    }
  }

  protected void updateFileIcon(VirtualFile file) {
    final int index = findEditorIndex(findFileComposite(file));
    LOG.assertTrue(index != -1);
    setIconAt(index, getFileIcon(file));
  }

  protected void updateFileName(VirtualFile file) {
    final int index = findEditorIndex(findFileComposite(file));
    if (index != -1) {
      setTitleAt(index, EditorTabbedContainer.calcTabTitle(getManager().getProject(), file));
      setToolTipTextAt(index, getManager().getFileTooltipText(file));
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
      pinIcon = PIN_ICON;
    }
    else {
      pinIcon = null;
    }

    final Icon modifiedIcon;
    if (UISettings.getInstance().MARK_MODIFIED_TABS_WITH_ASTERISK) {
      modifiedIcon = composite != null && composite.isModified() ? MODIFIED_ICON : GAP_ICON;
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
    if (modifiedIcon != null) result.setIcon(modifiedIcon, i++);

    return result;
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
    if (myTabbedPane != null) {
      parent.add(myTabbedPane.getComponent(), BorderLayout.CENTER);
    }
    else {
      if (myPanel.getComponentCount() > 0) {
        parent.add(myPanel.getComponent(0), BorderLayout.CENTER);
      }
    }
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
    if (myTabbedPane != null && getTabCount() < UISettings.getInstance().EDITOR_TAB_LIMIT && findFileComposite(siblingEditor.getFile()) == null) {
      setEditor(siblingEditor, true);
    }
    else if (myTabbedPane == null && getTabCount() == 0) { // tabless mode and no file opened
      setEditor(siblingEditor, true);
    }
    else {
      getManager().disposeComposite(siblingEditor);
    }
  }

  public void unsplitAll() {
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
  public EditorWithProviderComposite findFileComposite(final VirtualFile file) {
    for (int i = 0; i != getTabCount(); ++i) {
      final EditorWithProviderComposite editor = getEditorAt(i);
      if (editor.getFile().equals(file)) {
        return editor;
      }
    }
    return null;
  }


  public int findComponentIndex(final Component component) {
    for (int i = 0; i != getTabCount(); ++i) {
      final EditorWithProviderComposite editor = getEditorAt(i);
      if (editor.getComponent ().equals (component)) {
        return i;
      }
    }
    return -1;
  }

  public int findEditorIndex(final EditorComposite editorToFind) {
    for (int i = 0; i != getTabCount(); ++i) {
      final EditorWithProviderComposite editor = getEditorAt(i);
      if (editor.equals (editorToFind)) {
        return i;
      }
    }
    return -1;
  }

  public int findFileIndex(final VirtualFile fileToFind) {
    for (int i = 0; i != getTabCount(); ++i) {
      final VirtualFile file = getFileAt(i);
      if (file.equals (fileToFind)) {
        return i;
      }
    }
    return -1;
  }

  private EditorWithProviderComposite getEditorAt(final int i) {
    final TComp comp;
    if (myTabbedPane != null) {
      comp = (TComp)myTabbedPane.getComponentAt(i);
    }
    else {
      LOG.assertTrue(i <= 1);
      comp = (TComp)myPanel.getComponent(i);
    }
    return comp.myEditor;
  }

  public boolean isFileOpen(final VirtualFile file) {
    return findFileComposite(file) != null;
  }

  public boolean isFilePinned(final VirtualFile file) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final EditorComposite editorComposite = findFileComposite(file);
    if (editorComposite == null) {
      throw new IllegalArgumentException("file is not open: " + file.getPath());
    }
    return editorComposite.isPinned();
  }

  public void setFilePinned(final VirtualFile file, final boolean pinned) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final EditorComposite editorComposite = findFileComposite(file);
    if (editorComposite == null) {
      throw new IllegalArgumentException("file is not open: " + file.getPath());
    }
    editorComposite.setPinned(pinned);
    updateFileIcon(file);
  }

  void trimToSize(final int limit, @Nullable final VirtualFile fileToIgnore, final boolean transferFocus) {
    if (myTabbedPane == null) {
      return;
    }

    FileEditorManagerEx.getInstanceEx(getManager().getProject()).getReady(this).doWhenDone(new Runnable() {
      @Override
      public void run() {
        final boolean closeNonModifiedFilesFirst = UISettings.getInstance().CLOSE_NON_MODIFIED_FILES_FIRST;
        final EditorComposite selectedComposite = getSelectedEditor();
        try {
          doTrimSize(limit, fileToIgnore, closeNonModifiedFilesFirst, transferFocus);
        }
        finally {
          setSelectedEditor(selectedComposite, false);
        }
      }
    });
  }

  private void doTrimSize(int limit, @Nullable VirtualFile fileToIgnore, boolean closeNonModifiedFilesFirst, boolean transferFocus) {
    while_label:
    while (myTabbedPane.getTabCount() > limit && myTabbedPane.getTabCount() > 0) {
      // If all tabs are pinned then do nothings. Othrwise we will get infinitive loop
      boolean allTabsArePinned = true;
      for (int i = myTabbedPane.getTabCount() - 1; i >= 0; i--) {
        final VirtualFile file = getFileAt(i);
        if (fileCanBeClosed(file, fileToIgnore)) {
          allTabsArePinned = false;
          break;
        }
      }
      if (allTabsArePinned) {
        return;
      }

      // Try to close non-modified files first (is specified in option)
      if (closeNonModifiedFilesFirst) {
        // Search in history
        final VirtualFile[] allFiles = getFiles();
        final VirtualFile[] histFiles = EditorHistoryManager.getInstance(getManager ().getProject()).getFiles();

        // first, we search for files not in history
        for (int i = 0; i != allFiles.length; ++ i) {
          final VirtualFile file = allFiles[i];
          if (fileCanBeClosed(file, fileToIgnore)) {
            boolean found = false;
            for (int j = 0; j != histFiles.length; j++) {
              if (histFiles[j] == file) {
                found = true;
                break;
              }
            }
            if (!found) {
              defaultCloseFile(file, transferFocus);
              continue while_label;
            }
          }
        }

        for (final VirtualFile file : histFiles) {
          if (!fileCanBeClosed(file, fileToIgnore)) {
            continue;
          }

          final EditorComposite composite = findFileComposite(file);
          //LOG.assertTrue(composite != null);
          if (composite != null && composite.getInitialFileTimeStamp() == file.getTimeStamp()) {
            // we found non modified file
            defaultCloseFile(file, transferFocus);
            continue while_label;
          }
        }

        // Search in tabbed pane
        final VirtualFile selectedFile = getSelectedFile();
        for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
          final VirtualFile file = getFileAt(i);
          final EditorComposite composite = getEditorAt(i);
          if (!fileCanBeClosed(file, fileToIgnore)) {
            continue;
          }
          if (!selectedFile.equals(file)) {
            if (composite.getInitialFileTimeStamp() == file.getTimeStamp()) {
              // we found non modified file
              defaultCloseFile(file, transferFocus);
              continue while_label;
            }
          }
        }
      }

      // It's non enough to close non-modified files only. Try all other files.
      // Search in history from less frequently used.
      {
        final VirtualFile[]  allFiles = getFiles();
        final VirtualFile[] histFiles = EditorHistoryManager.getInstance(getManager ().getProject()).getFiles();

        // first, we search for files not in history
        for (int i = 0; i != allFiles.length; ++ i) {
          final VirtualFile file = allFiles[i];
          if (fileCanBeClosed(file, fileToIgnore)) {
            boolean found = false;
            for (int j = 0; j != histFiles.length; j++) {
              if (histFiles[j] == file) {
                found = true;
                break;
              }
            }
            if (!found) {
              defaultCloseFile(file, transferFocus);
              continue while_label;
            }
          }
        }


        for (final VirtualFile file : histFiles) {
          if (fileCanBeClosed(file, fileToIgnore)) {
            defaultCloseFile(file, transferFocus);
            continue while_label;
          }
        }
      }

      // Close first opened file in tabbed pane that isn't a selected one
      {
        final VirtualFile selectedFile = getSelectedFile();
        for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
          final VirtualFile file = getFileAt(i);
          if (!fileCanBeClosed(file, fileToIgnore)) {
            continue;
          }
          if (!selectedFile.equals(file)) {
            defaultCloseFile(file, transferFocus);
            continue while_label;
          }
          else if (i == myTabbedPane.getTabCount() - 1) {
            // if file is selected one and it's last file that we have no choice as close it
            defaultCloseFile(file, transferFocus);
            continue while_label;
          }
        }
      }
    }
  }

  private void defaultCloseFile(VirtualFile file, boolean transferFocus) {
    closeFile(file, true, transferFocus);
  }

  private boolean fileCanBeClosed(final VirtualFile file, @Nullable final VirtualFile fileToIgnore) {
    return isFileOpen (file) && !file.equals(fileToIgnore) && !isFilePinned(file);
  }

  protected VirtualFile getFileAt(int i) {
    return getEditorAt(i).getFile();
  }

  @Override
  public String toString() {
    return "EditorWindow: files=" + Arrays.asList(getFiles());
  }
}
