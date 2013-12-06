/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.text.FileDropHandler;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.MacKeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.FrameTitleBuilder;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.IdePanePanel;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.util.Alarm;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ArrayListSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ContainerEvent;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;


/**
 * Author: msk
 */
public class EditorsSplitters extends IdePanePanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.EditorsSplitters");
  private static final String PINNED = "pinned";

  private EditorWindow myCurrentWindow;
  final Set<EditorWindow> myWindows = new CopyOnWriteArraySet<EditorWindow>();

  private final FileEditorManagerImpl myManager;
  private Element mySplittersElement;  // temporarily used during initialization
  int myInsideChange = 0;
  private final MyFocusWatcher myFocusWatcher;
  private final Alarm myIconUpdaterAlarm = new Alarm();

  public EditorsSplitters(final FileEditorManagerImpl manager, DockManager dockManager, boolean createOwnDockableContainer) {
    super(new BorderLayout());
    myManager = manager;
    myFocusWatcher = new MyFocusWatcher();
    setFocusTraversalPolicy(new MyFocusTraversalPolicy());
    setTransferHandler(new MyTransferHandler());
    clear();

    if (createOwnDockableContainer) {
      DockableEditorTabbedContainer dockable = new DockableEditorTabbedContainer(myManager.getProject(), this, false);
      Disposer.register(manager.getProject(), dockable);
      dockManager.register(dockable);
    }
  }
  
  public FileEditorManagerImpl getManager() {
    return myManager;
  }

  public void clear() {
    removeAll();
    myWindows.clear();
    setCurrentWindow(null);
    repaint (); // revalidate doesn't repaint correctly after "Close All"
  }

  public void startListeningFocus() {
    myFocusWatcher.install(this);
  }

  private void stopListeningFocus() {
    myFocusWatcher.deinstall(this);
  }

  public void dispose() {
    myIconUpdaterAlarm.cancelAllRequests();
    stopListeningFocus();
  }

  @Nullable
  public VirtualFile getCurrentFile() {
    if (myCurrentWindow != null) {
      return myCurrentWindow.getSelectedFile();
    }
    return null;
  }


  private boolean showEmptyText() {
    return myCurrentWindow == null || myCurrentWindow.getFiles().length == 0;
  }

  private boolean isProjectViewVisible() {
    final Window frame = SwingUtilities.getWindowAncestor(this);
    if (frame instanceof IdeFrameImpl) {
      final Project project = ((IdeFrameImpl)frame).getProject();
      if (project != null) {
        if (!project.isInitialized()) return true;
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW);
        return toolWindow != null && toolWindow.isVisible();
      }
    }

    return false;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (myCurrentWindow == null || myCurrentWindow.getFiles().length == 0) {
      g.setColor(UIUtil.isUnderDarcula()? UIUtil.getBorderColor() : new Color(0, 0, 0, 50));
      g.drawLine(0, 0, getWidth(), 0);
    }

    boolean isDarkBackground = UIUtil.isUnderDarcula();

    if (showEmptyText()) {
      UIUtil.applyRenderingHints(g);
      GraphicsUtil.setupAntialiasing(g, true, false);
      g.setColor(new JBColor(isDarkBackground ? Gray._230 : Gray._80, Gray._160));
      g.setFont(UIUtil.getLabelFont().deriveFont(isDarkBackground ? 24f : 20f));

      final UIUtil.TextPainter painter = new UIUtil.TextPainter().withLineSpacing(1.5f);
      painter.withShadow(true, new JBColor(Gray._200.withAlpha(100), Gray._0.withAlpha(255)));

      painter.appendLine("No files are open").underlined(new JBColor(Gray._150, Gray._180));

      final Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE);
      final String everywhere;
      if (shortcuts.length == 0) {
        everywhere = "Search Everywhere with <shortcut>Double " + (SystemInfo.isMac ? MacKeymapUtil.SHIFT : "Shift");
      } else {
        everywhere = "Search Everywhere <shortcut>" + KeymapUtil.getShortcutsText(shortcuts);
      }
      painter.appendLine(everywhere + "</shortcut>").smaller().withBullet();

      if (!isProjectViewVisible()) {
        painter.appendLine("Open Project View with <shortcut>" + KeymapUtil.getShortcutText(new KeyboardShortcut(
          KeyStroke.getKeyStroke((SystemInfo.isMac ? "meta" : "alt") + " 1"), null)) + "</shortcut>").smaller().withBullet();
      }

      painter.appendLine("Open a file by name with " + getActionShortcutText("GotoFile")).smaller().withBullet()
        .appendLine("Open Recent files with " + getActionShortcutText(IdeActions.ACTION_RECENT_FILES)).smaller().withBullet()
        .appendLine("Open Navigation Bar with " + getActionShortcutText("ShowNavBar")).smaller().withBullet()
        .appendLine("Drag and Drop file(s) here from " + ShowFilePathAction.getFileManagerName()).smaller().withBullet()
        .draw(g, new PairFunction<Integer, Integer, Pair<Integer, Integer>>() {
          @Override
          public Pair<Integer, Integer> fun(Integer width, Integer height) {
            final Dimension s = getSize();
            return Pair.create((s.width - width) / 2, (s.height - height) / 2);
          }
        });
    }
  }

  private static String getActionShortcutText(final String actionId) {
    final Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionId);
    String shortcutText = "";
    for (final Shortcut shortcut : shortcuts) {
      if (shortcut instanceof KeyboardShortcut) {
        shortcutText = KeymapUtil.getShortcutText(shortcut);
        break;
      }
    }

    return "<shortcut>" + shortcutText + "</shortcut>";
  }

  public void writeExternal(final Element element) {
    if (getComponentCount() != 0) {
      final Component comp = getComponent(0);
      LOG.assertTrue(comp instanceof JPanel);
      final JPanel panel = (JPanel)comp;
      if (panel.getComponentCount() != 0) {
        final Element res = writePanel(panel);
        element.addContent(res);
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private Element writePanel(final JPanel panel) {
    final Component comp = panel.getComponent(0);
    if (comp instanceof Splitter) {
      final Splitter splitter = (Splitter)comp;
      final Element res = new Element("splitter");
      res.setAttribute("split-orientation", splitter.getOrientation() ? "vertical" : "horizontal");
      res.setAttribute("split-proportion", Float.toString(splitter.getProportion()));
      final Element first = new Element("split-first");
      first.addContent(writePanel((JPanel)splitter.getFirstComponent()));
      final Element second = new Element("split-second");
      second.addContent(writePanel((JPanel)splitter.getSecondComponent()));
      res.addContent(first);
      res.addContent(second);
      return res;
    }
    else if (comp instanceof JBTabs) {
      final Element res = new Element("leaf");
      final EditorWindow window = findWindowWith(comp);
      writeWindow(res, window);
      return res;
    }
    else if (comp instanceof EditorWindow.TCompForTablessMode) {
      final EditorWithProviderComposite composite = ((EditorWindow.TCompForTablessMode)comp).myEditor;
      final Element res = new Element("leaf");
      writeComposite(res, composite.getFile(), composite, false, composite);
      return res;
    }
    else {
      LOG.error(comp != null ? comp.getClass().getName() : null);
      return null;
    }
  }

  private void writeWindow(final Element res, final EditorWindow window) {
    if (window != null) {
      final EditorWithProviderComposite[] composites = window.getEditors();
      for (int i = 0; i < composites.length; i++) {
        final VirtualFile file = window.getFileAt(i);
        final boolean isPinned = window.isFilePinned(file);
        final EditorWithProviderComposite composite = composites[i];
        final EditorWithProviderComposite selectedEditor = window.getSelectedEditor();

        writeComposite(res, file, composite, isPinned, selectedEditor);
      }
    }
  }

  private void writeComposite(final Element res, final VirtualFile file, final EditorWithProviderComposite composite,
                              final boolean pinned,
                              final EditorWithProviderComposite selectedEditor) {
    final Element fileElement = new Element("file");
    fileElement.setAttribute("leaf-file-name", file.getName()); // TODO: all files
    final HistoryEntry entry = composite.currentStateAsHistoryEntry();
    entry.writeExternal(fileElement, getManager().getProject());
    fileElement.setAttribute(PINNED,         Boolean.toString(pinned));
    fileElement.setAttribute("current",        Boolean.toString(composite.equals (getManager ().getLastSelected ())));
    fileElement.setAttribute("current-in-tab", Boolean.toString(composite.equals (selectedEditor)));
    res.addContent(fileElement);
  }

  public void openFiles() {
    if (mySplittersElement != null) {
      Ref<EditorWindow> currentWindow = new Ref<EditorWindow>();
      final JPanel comp = readExternalPanel(mySplittersElement, getTopPanel(), currentWindow);
      if (comp != null) {
        removeAll();
        add(comp, BorderLayout.CENTER);
        mySplittersElement = null;
      }
      // clear empty splitters
      for (EditorWindow window : getWindows()) {
        if (window.getEditors().length == 0) {
          for (EditorWindow sibling : window.findSiblings()) {
            sibling.unsplit(false);
          }
        }
      }
      if (!currentWindow.isNull()) {
        setCurrentWindow(currentWindow.get(), true);
      }
    }
  }

  public void readExternal(final Element element) {
    mySplittersElement = element;
  }

  @Nullable
  @SuppressWarnings({"HardCodedStringLiteral"})
  private JPanel readExternalPanel(final Element element, @Nullable JPanel panel, Ref<EditorWindow> currentWindow) {
    final Element splitterElement = element.getChild("splitter");
    if (splitterElement != null) {
      return readSplitter(panel, splitterElement, currentWindow);
    }

    final Element leaf = element.getChild("leaf");
    if (leaf == null) {
      return null;
    }

    final EditorWindow window = panel == null ? new EditorWindow(this) : findWindowWith(panel);
    LOG.assertTrue(window != null);

    @SuppressWarnings("unchecked") final List<Element> children = ContainerUtil.newArrayList(leaf.getChildren("file"));
    if (UISettings.getInstance().ACTIVATE_RIGHT_EDITOR_ON_CLOSE) {
      Collections.reverse(children);
    }

    // trim to EDITOR_TAB_LIMIT, ignoring CLOSE_NON_MODIFIED_FILES_FIRST policy
    for (Iterator<Element> iterator = children.iterator(); iterator.hasNext() && UISettings.getInstance().EDITOR_TAB_LIMIT < children.size(); ) {
      Element child = iterator.next();
      if (!Boolean.valueOf(child.getAttributeValue(PINNED)).booleanValue()) {
        iterator.remove();
      }
    }

    VirtualFile currentFile = null;
    for (int i = 0; i < children.size(); i++) {
      final Element file = children.get(i);
      try {
        final FileEditorManagerImpl fileEditorManager = getManager();
        final HistoryEntry entry = new HistoryEntry(fileEditorManager.getProject(), file.getChild(HistoryEntry.TAG), true);
        final boolean isCurrent = Boolean.valueOf(file.getAttributeValue("current")).booleanValue();
        fileEditorManager.openFileImpl4(window, entry.myFile, false, entry, isCurrent, i);
        if (fileEditorManager.isFileOpen(entry.myFile)) {
          window.setFilePinned(entry.myFile, Boolean.valueOf(file.getAttributeValue(PINNED)).booleanValue());
          if (Boolean.valueOf(file.getAttributeValue("current-in-tab")).booleanValue()) {
            currentFile = entry.myFile;
          }
        }

      }
      catch (InvalidDataException e) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          LOG.error(e);
        }
      }
    }
    if (currentFile != null) {
      final EditorComposite editor = window.findFileComposite(currentFile);
      if (editor != null) {
        window.setSelectedEditor(editor, true);
      }
    }
    return window.myPanel;
  }

  private JPanel readSplitter(JPanel panel, Element splitterElement, Ref<EditorWindow> currentWindow) {
    final boolean orientation = "vertical".equals(splitterElement.getAttributeValue("split-orientation"));
    final float proportion = Float.valueOf(splitterElement.getAttributeValue("split-proportion")).floatValue();
    final Element first = splitterElement.getChild("split-first");
    final Element second = splitterElement.getChild("split-second");

    Splitter splitter;
    if (panel == null) {
      panel = new JPanel(new BorderLayout());
      panel.setOpaque(false);
      splitter = new Splitter(orientation, proportion, 0.1f, 0.9f);
      panel.add(splitter, BorderLayout.CENTER);
      splitter.setFirstComponent(readExternalPanel(first, null, currentWindow));
      splitter.setSecondComponent(readExternalPanel(second, null, currentWindow));
    }
    else if (panel.getComponent(0) instanceof Splitter) {
      splitter = (Splitter)panel.getComponent(0);
      readExternalPanel(first, (JPanel)splitter.getFirstComponent(), currentWindow);
      readExternalPanel(second, (JPanel)splitter.getSecondComponent(), currentWindow);
    }
    else {
      readExternalPanel(first, panel, currentWindow);
      readExternalPanel(second, panel, currentWindow);
    }
    return panel;
  }

  @NotNull public VirtualFile[] getOpenFiles() {
    final ArrayListSet<VirtualFile> files = new ArrayListSet<VirtualFile>();
    for (final EditorWindow myWindow : myWindows) {
      final EditorWithProviderComposite[] editors = myWindow.getEditors();
      for (final EditorWithProviderComposite editor : editors) {
        VirtualFile file = editor.getFile();
        // background thread may call this method when invalid file is being removed
        // do not return it here as it will quietly drop out soon
        if (file.isValid()) {
          files.add(file);
        }
      }
    }
    return VfsUtil.toVirtualFileArray(files);
  }

  @NotNull public VirtualFile[] getSelectedFiles() {
    final ArrayListSet<VirtualFile> files = new ArrayListSet<VirtualFile>();
    for (final EditorWindow window : myWindows) {
      final VirtualFile file = window.getSelectedFile();
      if (file != null) {
        files.add(file);
      }
    }
    final VirtualFile[] virtualFiles = VfsUtil.toVirtualFileArray(files);
    final VirtualFile currentFile = getCurrentFile();
    if (currentFile != null) {
      for (int i = 0; i != virtualFiles.length; ++i) {
        if (Comparing.equal(virtualFiles[i], currentFile)) {
          virtualFiles[i] = virtualFiles[0];
          virtualFiles[0] = currentFile;
          break;
        }
      }
    }
    return virtualFiles;
  }

  @NotNull public FileEditor[] getSelectedEditors() {
    final List<FileEditor> editors = new ArrayList<FileEditor>();
    final EditorWindow currentWindow = getCurrentWindow();
    if (currentWindow != null) {
      final EditorWithProviderComposite composite = currentWindow.getSelectedEditor();
      if (composite != null) {
        editors.add (composite.getSelectedEditor());
      }
    }

    for (final EditorWindow window : myWindows) {
      if (!window.equals(currentWindow)) {
        final EditorWithProviderComposite composite = window.getSelectedEditor();
        if (composite != null) {
          editors.add(composite.getSelectedEditor());
        }
      }
    }
    return editors.toArray(new FileEditor[editors.size()]);
  }

  public void updateFileIcon(@NotNull final VirtualFile file) {
    updateFileIconLater(file);
  }

  private void updateFileIconImmediately(final VirtualFile file) {
    final Collection<EditorWindow> windows = findWindows(file);
    for (EditorWindow window : windows) {
      window.updateFileIcon(file);
    }
  }

  private final Set<VirtualFile> myFilesToUpdateIconsFor = new HashSet<VirtualFile>();

  private void updateFileIconLater(VirtualFile file) {
    myFilesToUpdateIconsFor.add(file);
    myIconUpdaterAlarm.cancelAllRequests();
    myIconUpdaterAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (myManager.getProject().isDisposed()) return;
        for (VirtualFile file : myFilesToUpdateIconsFor) {
          updateFileIconImmediately(file);
        }
        myFilesToUpdateIconsFor.clear();
      }
    }, 200, ModalityState.stateForComponent(this));
  }

  public void updateFileColor(@NotNull final VirtualFile file) {
    final Collection<EditorWindow> windows = findWindows(file);
    for (final EditorWindow window : windows) {
      final int index = window.findEditorIndex(window.findFileComposite(file));
      LOG.assertTrue(index != -1);
      window.setForegroundAt(index, getManager().getFileColor(file));
      window.setWaveColor(index, getManager().isProblem(file) ? JBColor.red : null);
    }
  }

  public void trimToSize(final int editor_tab_limit) {
    for (final EditorWindow window : myWindows) {
      window.trimToSize(editor_tab_limit, null, true);
    }
  }

  public void setTabsPlacement(final int tabPlacement) {
    final EditorWindow[] windows = getWindows();
    for (int i = 0; i != windows.length; ++ i) {
      windows[i].setTabsPlacement(tabPlacement);
    }
  }

  public void setTabLayoutPolicy(int scrollTabLayout) {
    final EditorWindow[] windows = getWindows();
    for (int i = 0; i != windows.length; ++ i) {
      windows[i].setTabLayoutPolicy(scrollTabLayout);
    }
  }

  public void updateFileName(final VirtualFile updatedFile) {
    final EditorWindow[] windows = getWindows();
    for (int i = 0; i != windows.length; ++ i) {
      windows [i].updateFileName(updatedFile);
    }

    Project project = myManager.getProject();

    final IdeFrame frame = getFrame(project);
    if (frame != null) {
      VirtualFile file = getCurrentFile();

      File ioFile = file == null ? null : new File(file.getPresentableUrl());
      String fileTitle = file == null ? null : FrameTitleBuilder.getInstance().getFileTitle(project, file);

      frame.setFileTitle(fileTitle, ioFile);
    }
  }

  protected IdeFrame getFrame(Project project) {
    final WindowManagerEx windowManagerEx = WindowManagerEx.getInstanceEx();
    final IdeFrame frame = windowManagerEx.getFrame(project);
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode() || frame != null);
    return frame;
  }

  public boolean isInsideChange() {
    return myInsideChange > 0;
  }

  private void setCurrentWindow(@Nullable final EditorWindow currentWindow) {
    myCurrentWindow = currentWindow;
  }

  public void updateFileBackgroundColor(final VirtualFile file) {
    final EditorWindow[] windows = getWindows();
    for (int i = 0; i != windows.length; ++ i) {
      windows [i].updateFileBackgroundColor(file);
    }
  }

  public int getSplitCount() {
    if (getComponentCount() > 0) {
      JPanel panel = (JPanel) getComponent(0);
      return getSplitCount(panel);
    }
    return 0;
  }

  private static int getSplitCount(JComponent component) {
    if (component.getComponentCount() > 0) {
      final JComponent firstChild = (JComponent)component.getComponent(0);
      if (firstChild instanceof Splitter) {
        final Splitter splitter = (Splitter)firstChild;
        return getSplitCount(splitter.getFirstComponent()) + getSplitCount(splitter.getSecondComponent());
      }
      return 1;
    }
    return 0;
  }

  protected void afterFileClosed(VirtualFile file) {
  }

  protected void afterFileOpen(VirtualFile file) {
  }

  @Nullable
  public JBTabs getTabsAt(RelativePoint point) {
    Point thisPoint = point.getPoint(this);
    Component c = SwingUtilities.getDeepestComponentAt(this, thisPoint.x, thisPoint.y);
    while (c != null) {
      if (c instanceof JBTabs) {
        return (JBTabs)c;
      }
      c = c.getParent();
    }

    return null;
  }

  public boolean isEmptyVisible() {
    EditorWindow[] windows = getWindows();
    for (EditorWindow each : windows) {
      if (!each.isEmptyVisible()) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public VirtualFile findNextFile(final VirtualFile file) {
    final EditorWindow[] windows = getWindows(); // TODO: use current file as base
    for (int i = 0; i != windows.length; ++i) {
      final VirtualFile[] files = windows[i].getFiles();
      for (final VirtualFile fileAt : files) {
        if (!Comparing.equal(fileAt, file)) {
          return fileAt;
        }
      }
    }
    return null;
  }

  void closeFile(VirtualFile file, boolean moveFocus) {
    final List<EditorWindow> windows = findWindows(file);
    if (!windows.isEmpty()) {
      final VirtualFile nextFile = findNextFile(file);
      for (final EditorWindow window : windows) {
        LOG.assertTrue(window.getSelectedEditor() != null);
        window.closeFile(file, false, moveFocus);
        if (window.getTabCount() == 0 && nextFile != null) {
          EditorWithProviderComposite newComposite = myManager.newEditorComposite(nextFile);
          window.setEditor(newComposite, moveFocus); // newComposite can be null
        }
      }
      // cleanup windows with no tabs
      for (final EditorWindow window : windows) {
        if (window.isDisposed()) {
          // call to window.unsplit() which might make its sibling disposed
          continue;
        }
        if (window.getTabCount() == 0) {
          window.unsplit(false);
        }
      }
    }
  }

  private final class MyFocusTraversalPolicy extends IdeFocusTraversalPolicy {
    @Override
    public final Component getDefaultComponentImpl(final Container focusCycleRoot) {
      if (myCurrentWindow != null) {
        final EditorWithProviderComposite selectedEditor = myCurrentWindow.getSelectedEditor();
        if (selectedEditor != null) {
          return IdeFocusTraversalPolicy.getPreferredFocusedComponent(selectedEditor.getComponent(), this);
        }
      }
      return IdeFocusTraversalPolicy.getPreferredFocusedComponent(EditorsSplitters.this, this);
    }
  }

  @Nullable
  public JPanel getTopPanel() {
    return getComponentCount() > 0 ? (JPanel)getComponent(0) : null;
  }

  public EditorWindow getCurrentWindow() {
    return myCurrentWindow;
  }

  public EditorWindow getOrCreateCurrentWindow(final VirtualFile file) {
    final List<EditorWindow> windows = findWindows(file);
    if (getCurrentWindow() == null) {
      final Iterator<EditorWindow> iterator = myWindows.iterator();
      if (!windows.isEmpty()) {
        setCurrentWindow(windows.get(0), false);
      }
      else if (iterator.hasNext()) {
        setCurrentWindow(iterator.next(), false);
      }
      else {
        createCurrentWindow();
      }
    }
    else if (!windows.isEmpty()) {
      if (!windows.contains(getCurrentWindow())) {
        setCurrentWindow(windows.get(0), false);
      }
    }
    return getCurrentWindow();
  }

  public void createCurrentWindow() {
    LOG.assertTrue(myCurrentWindow == null);
    setCurrentWindow(new EditorWindow(this));
    add(myCurrentWindow.myPanel, BorderLayout.CENTER);
  }

  /**
   * sets the window passed as a current ('focused') window among all splitters. All file openings will be done inside this
   * current window
   * @param window a window to be set as current
   * @param requestFocus whether to request focus to the editor currently selected in this window
   */
  public void setCurrentWindow(@Nullable final EditorWindow window, final boolean requestFocus) {
    final EditorWithProviderComposite newEditor = window != null? window.getSelectedEditor() : null;

    Runnable fireRunnable = new Runnable() {
      @Override
      public void run() {
        getManager().fireSelectionChanged(newEditor);
      }
    };

    setCurrentWindow(window);

    getManager().updateFileName(window == null ? null : window.getSelectedFile());

    if (window != null) {
      final EditorWithProviderComposite selectedEditor = window.getSelectedEditor();
      if (selectedEditor != null) {
        fireRunnable.run();
      }

      if (requestFocus) {
        window.requestFocus(true);
      }
    } else {
      fireRunnable.run();
    }
  }

  //---------------------------------------------------------

  public EditorWithProviderComposite[] getEditorsComposites() {
    final ArrayList<EditorWithProviderComposite> res = new ArrayList<EditorWithProviderComposite>();

    for (final EditorWindow myWindow : myWindows) {
      final EditorWithProviderComposite[] editors = myWindow.getEditors();
      ContainerUtil.addAll(res, editors);
    }
    return res.toArray(new EditorWithProviderComposite[res.size()]);
  }

  //---------------------------------------------------------

  @NotNull
  public List<EditorWithProviderComposite> findEditorComposites(final VirtualFile file) {
    final ArrayList<EditorWithProviderComposite> res = new ArrayList<EditorWithProviderComposite>();
    for (final EditorWindow window : myWindows) {
      final EditorWithProviderComposite fileComposite = window.findFileComposite(file);
      if (fileComposite != null) {
        res.add(fileComposite);
      }
    }
    return res;
  }

  @NotNull
  public List<EditorWindow> findWindows(final VirtualFile file) {
    final ArrayList<EditorWindow> res = new ArrayList<EditorWindow>();
    for (final EditorWindow window : myWindows) {
      if (window.findFileComposite(file) != null) {
        res.add(window);
      }
    }
    return res;
  }

  @NotNull public EditorWindow [] getWindows() {
    return myWindows.toArray(new EditorWindow [myWindows.size()]);
  }

  @NotNull public EditorWindow[] getOrderedWindows() {
    final ArrayList<EditorWindow> res = new ArrayList<EditorWindow>();

    // Collector for windows in tree ordering:
    class Inner{
      final void collect(final JPanel panel){
        final Component comp = panel.getComponent(0);
        if (comp instanceof Splitter) {
          final Splitter splitter = (Splitter)comp;
          collect((JPanel)splitter.getFirstComponent());
          collect((JPanel)splitter.getSecondComponent());
        }
        else if (comp instanceof JPanel || comp instanceof JBTabs) {
          final EditorWindow window = findWindowWith(comp);
          if (window != null) {
            res.add(window);
          }
        }
      }
    }

    // get root component and traverse splitters tree:
    if (getComponentCount() != 0) {
      final Component comp = getComponent(0);
      LOG.assertTrue(comp instanceof JPanel);
      final JPanel panel = (JPanel)comp;
      if (panel.getComponentCount() != 0) {
        new Inner().collect (panel);
      }
    }

    LOG.assertTrue(res.size() == myWindows.size());
    return res.toArray(new EditorWindow [res.size()]);
  }

  @Nullable
  private EditorWindow findWindowWith(final Component component) {
    if (component != null) {
      for (final EditorWindow window : myWindows) {
        if (SwingUtilities.isDescendingFrom(component, window.myPanel)) {
          return window;
        }
      }
    }
    return null;
  }

  public boolean isFloating() {
    return false;
  }

  private final class MyFocusWatcher extends FocusWatcher {
    @Override
    protected void focusedComponentChanged(final Component component, final AWTEvent cause) {
      EditorWindow newWindow = null;

      if (component != null) {
        newWindow = findWindowWith(component);
      }
      else if (cause instanceof ContainerEvent && cause.getID() == ContainerEvent.COMPONENT_REMOVED) {
        // do not change current window in case of child removal as in JTable.removeEditor
        // otherwise Escape in a toolwindow will not focus editor with JTable content
        return;
      }

      setCurrentWindow(newWindow);
      setCurrentWindow(newWindow, false);
    }
  }

  private final class MyTransferHandler extends TransferHandler {
    private final FileDropHandler myFileDropHandler = new FileDropHandler(null);

    @Override
    public boolean importData(JComponent comp, Transferable t) {
      if (myFileDropHandler.canHandleDrop(t.getTransferDataFlavors())) {
        myFileDropHandler.handleDrop(t, myManager.getProject(), myCurrentWindow);
        return true;
      }
      return false;
    }

    @Override
    public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
      return myFileDropHandler.canHandleDrop(transferFlavors);
    }
  }
}
