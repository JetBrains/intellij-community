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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.text.FileDropHandler;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.FrameTitleBuilder;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.openapi.wm.impl.IdePanePanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ArrayListSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
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

public class EditorsSplitters extends IdePanePanel implements UISettingsListener, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.EditorsSplitters");
  private static final String PINNED = "pinned";
  private static final String CURRENT_IN_TAB = "current-in-tab";

  private static final Key<Object> DUMMY_KEY = Key.create("EditorsSplitters.dummy.key");

  private EditorWindow myCurrentWindow;
  private final Set<EditorWindow> myWindows = new CopyOnWriteArraySet<>();

  private final FileEditorManagerImpl myManager;
  private Element mySplittersElement;  // temporarily used during initialization
  int myInsideChange;
  private final MyFocusWatcher myFocusWatcher;
  private final Alarm myIconUpdaterAlarm = new Alarm();
  private final UIBuilder myUIBuilder = new UIBuilder();

  EditorsSplitters(final FileEditorManagerImpl manager, DockManager dockManager, boolean createOwnDockableContainer) {
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
    KeymapManagerListener keymapListener = keymap -> {
      invalidate();
      repaint();
    };
    KeymapManager.getInstance().addKeymapManagerListener(keymapListener, this);
  }

  public FileEditorManagerImpl getManager() {
    return myManager;
  }

  public void clear() {
    for (EditorWindow window : myWindows) {
      window.dispose();
    }
    removeAll();
    myWindows.clear();
    setCurrentWindow(null);
    repaint (); // revalidate doesn't repaint correctly after "Close All"
  }

  void startListeningFocus() {
    myFocusWatcher.install(this);
  }

  private void stopListeningFocus() {
    myFocusWatcher.deinstall(this);
  }

  @Override
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

  @Override
  protected void paintComponent(Graphics g) {
    if (showEmptyText()) {
      Graphics2D gg = IdeBackgroundUtil.withFrameBackground(g, this);
      super.paintComponent(gg);
      g.setColor(UIUtil.isUnderDarcula() ? UIUtil.getBorderColor() : new Color(0, 0, 0, 50));
      g.drawLine(0, 0, getWidth(), 0);
    }
  }

  public void writeExternal(@NotNull Element element) {
    if (getComponentCount() == 0) {
      return;
    }

    JPanel panel = (JPanel)getComponent(0);
    if (panel.getComponentCount() != 0) {
      try {
        element.addContent(writePanel(panel.getComponent(0)));
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private Element writePanel(@NotNull Component comp) {
    if (comp instanceof Splitter) {
      final Splitter splitter = (Splitter)comp;
      final Element res = new Element("splitter");
      res.setAttribute("split-orientation", splitter.getOrientation() ? "vertical" : "horizontal");
      res.setAttribute("split-proportion", Float.toString(splitter.getProportion()));
      final Element first = new Element("split-first");
      first.addContent(writePanel(splitter.getFirstComponent().getComponent(0)));
      final Element second = new Element("split-second");
      second.addContent(writePanel(splitter.getSecondComponent().getComponent(0)));
      res.addContent(first);
      res.addContent(second);
      return res;
    }
    else if (comp instanceof JBTabs) {
      final Element res = new Element("leaf");
      Integer limit = UIUtil.getClientProperty(((JBTabs)comp).getComponent(), JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY);
      if (limit != null) {
        res.setAttribute(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY.toString(), String.valueOf(limit));
      }

      writeWindow(res, findWindowWith(comp));
      return res;
    }
    else if (comp instanceof EditorWindow.TCompForTablessMode) {
      EditorWithProviderComposite composite = ((EditorWindow.TCompForTablessMode)comp).myEditor;
      Element res = new Element("leaf");
      res.addContent(writeComposite(composite.getFile(), composite, false, composite));
      return res;
    }
    else {
      LOG.error(comp.getClass().getName());
      return null;
    }
  }

  private void writeWindow(@NotNull Element res, @Nullable EditorWindow window) {
    if (window != null) {
      EditorWithProviderComposite[] composites = window.getEditors();
      for (int i = 0; i < composites.length; i++) {
        VirtualFile file = window.getFileAt(i);
        res.addContent(writeComposite(file, composites[i], window.isFilePinned(file), window.getSelectedEditor()));
      }
    }
  }

  @NotNull
  private Element writeComposite(VirtualFile file, EditorWithProviderComposite composite, boolean pinned, EditorWithProviderComposite selectedEditor) {
    Element fileElement = new Element("file");
    fileElement.setAttribute("leaf-file-name", file.getName()); // TODO: all files
    composite.currentStateAsHistoryEntry().writeExternal(fileElement, getManager().getProject());
    fileElement.setAttribute(PINNED, Boolean.toString(pinned));
    fileElement.setAttribute(CURRENT_IN_TAB, Boolean.toString(composite.equals(selectedEditor)));
    return fileElement;
  }

  public void openFiles() {
    if (mySplittersElement != null) {
      final JPanel comp = myUIBuilder.process(mySplittersElement, getTopPanel());
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
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
      });
    }
  }

  public int getEditorsCount() {
    return mySplittersElement == null ? 0 : countFiles(mySplittersElement);
  }

  private double myProgressStep;

  public void setProgressStep(double step) { myProgressStep = step; }

  private void updateProgress() {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setFraction(indicator.getFraction() + myProgressStep);
    }
  }

  private static int countFiles(Element element) {
    Integer value = new ConfigTreeReader<Integer>() {
      @Override
      protected Integer processFiles(@NotNull List<Element> fileElements, @Nullable Integer context) {
        return fileElements.size();
      }

      @Override
      protected Integer processSplitter(@NotNull Element element, @Nullable Element firstChild, @Nullable Element secondChild, @Nullable Integer context) {
        Integer first = process(firstChild, null);
        Integer second = process(secondChild, null);
        return (first == null ? 0 : first) + (second == null ? 0 : second);
      }
    }.process(element, null);
    return value == null ? 0 : value;
  }

  public void readExternal(final Element element) {
    mySplittersElement = element;
  }

  @NotNull public VirtualFile[] getOpenFiles() {
    final Set<VirtualFile> files = new ArrayListSet<>();
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
    return VfsUtilCore.toVirtualFileArray(files);
  }

  @NotNull public VirtualFile[] getSelectedFiles() {
    final Set<VirtualFile> files = new ArrayListSet<>();
    for (final EditorWindow window : myWindows) {
      final VirtualFile file = window.getSelectedFile();
      if (file != null) {
        files.add(file);
      }
    }
    final VirtualFile[] virtualFiles = VfsUtilCore.toVirtualFileArray(files);
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

  @NotNull
  public FileEditor[] getSelectedEditors() {
    Set<EditorWindow> windows = new THashSet<>(myWindows);
    final EditorWindow currentWindow = getCurrentWindow();
    if (currentWindow != null) {
      windows.add(currentWindow);
    }
    List<FileEditor> editors = new ArrayList<>();
    for (final EditorWindow window : windows) {
      final EditorWithProviderComposite composite = window.getSelectedEditor();
      if (composite != null) {
        editors.add(composite.getSelectedEditor());
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

  private final Set<VirtualFile> myFilesToUpdateIconsFor = new HashSet<>();

  private void updateFileIconLater(VirtualFile file) {
    myFilesToUpdateIconsFor.add(file);
    myIconUpdaterAlarm.cancelAllRequests();
    myIconUpdaterAlarm.addRequest(() -> {
      if (myManager.getProject().isDisposed()) return;
      for (VirtualFile file1 : myFilesToUpdateIconsFor) {
        updateFileIconImmediately(file1);
      }
      myFilesToUpdateIconsFor.clear();
    }, 200, ModalityState.stateForComponent(this));
  }

  void updateFileColor(@NotNull final VirtualFile file) {
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
      window.trimToSize(editor_tab_limit, window.getSelectedFile(), true);
    }
  }

  public void setTabsPlacement(final int tabPlacement) {
    final EditorWindow[] windows = getWindows();
    for (int i = 0; i != windows.length; ++ i) {
      windows[i].setTabsPlacement(tabPlacement);
    }
  }

  void setTabLayoutPolicy(int scrollTabLayout) {
    final EditorWindow[] windows = getWindows();
    for (int i = 0; i != windows.length; ++ i) {
      windows[i].setTabLayoutPolicy(scrollTabLayout);
    }
  }

  void updateFileName(@Nullable VirtualFile updatedFile) {
    for (EditorWindow window : getWindows()) {
      for (VirtualFile file : window.getFiles()) {
        if (updatedFile == null || file.getName().equals(updatedFile.getName())) {
          window.updateFileName(file);
        }
      }
    }

    Project project = myManager.getProject();
    IdeFrame frame = getFrame(project);
    if (frame != null) {
      String fileTitle = null;
      File ioFile = null;

      VirtualFile file = getCurrentFile();
      if (file != null) {
        ioFile = new File(file.getPresentableUrl());
        fileTitle = FrameTitleBuilder.getInstance().getFileTitle(project, file);
      }

      frame.setFileTitle(fileTitle, ioFile);
    }
  }

  protected IdeFrame getFrame(Project project) {
    final WindowManagerEx windowManagerEx = WindowManagerEx.getInstanceEx();
    final IdeFrame frame = windowManagerEx.getFrame(project);
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode() || frame != null);
    return frame;
  }

  boolean isInsideChange() {
    return myInsideChange > 0;
  }

  private void setCurrentWindow(@Nullable final EditorWindow currentWindow) {
    if (currentWindow != null && !myWindows.contains(currentWindow)) {
      throw new IllegalArgumentException(currentWindow + " is not a member of this container");
    }
    myCurrentWindow = currentWindow;
  }

  void updateFileBackgroundColor(@NotNull VirtualFile file) {
    final EditorWindow[] windows = getWindows();
    for (int i = 0; i != windows.length; ++ i) {
      windows [i].updateFileBackgroundColor(file);
    }
  }

  int getSplitCount() {
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
  JBTabs getTabsAt(RelativePoint point) {
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

  boolean isEmptyVisible() {
    EditorWindow[] windows = getWindows();
    for (EditorWindow each : windows) {
      if (!each.isEmptyVisible()) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  private VirtualFile findNextFile(final VirtualFile file) {
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
        if (window.getTabCount() == 0 && nextFile != null && myManager.getProject().isOpen()) {
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

  @Override
  public void uiSettingsChanged(UISettings uiSettings) {
    if (!myManager.getProject().isOpen()) return;
    for (VirtualFile file : getOpenFiles()) {
      updateFileBackgroundColor(file);
      updateFileIcon(file);
      updateFileColor(file);
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

  void createCurrentWindow() {
    LOG.assertTrue(myCurrentWindow == null);
    setCurrentWindow(createEditorWindow());
    add(myCurrentWindow.myPanel, BorderLayout.CENTER);
  }

  protected EditorWindow createEditorWindow() {
    return new EditorWindow(this);
  }

  /**
   * sets the window passed as a current ('focused') window among all splitters. All file openings will be done inside this
   * current window
   * @param window a window to be set as current
   * @param requestFocus whether to request focus to the editor currently selected in this window
   */
  void setCurrentWindow(@Nullable final EditorWindow window, final boolean requestFocus) {
    final EditorWithProviderComposite newEditor = window == null ? null : window.getSelectedEditor();

    Runnable fireRunnable = () -> getManager().fireSelectionChanged(newEditor);

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

  void addWindow(EditorWindow window) {
    myWindows.add(window);
  }

  void removeWindow(EditorWindow window) {
    myWindows.remove(window);
    if (myCurrentWindow == window) {
      myCurrentWindow = null;
    }
  }

  boolean containsWindow(EditorWindow window) {
    return myWindows.contains(window);
  }

  //---------------------------------------------------------

  public EditorWithProviderComposite[] getEditorsComposites() {
    List<EditorWithProviderComposite> res = new ArrayList<>();

    for (final EditorWindow myWindow : myWindows) {
      final EditorWithProviderComposite[] editors = myWindow.getEditors();
      ContainerUtil.addAll(res, editors);
    }
    return res.toArray(new EditorWithProviderComposite[res.size()]);
  }

  //---------------------------------------------------------

  @NotNull
  public List<EditorWithProviderComposite> findEditorComposites(@NotNull VirtualFile file) {
    List<EditorWithProviderComposite> res = new ArrayList<>();
    for (final EditorWindow window : myWindows) {
      final EditorWithProviderComposite fileComposite = window.findFileComposite(file);
      if (fileComposite != null) {
        res.add(fileComposite);
      }
    }
    return res;
  }

  @NotNull
  private List<EditorWindow> findWindows(final VirtualFile file) {
    List<EditorWindow> res = new ArrayList<>();
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

  @NotNull
  EditorWindow[] getOrderedWindows() {
    final List<EditorWindow> res = new ArrayList<>();

    // Collector for windows in tree ordering:
    class Inner{
      private void collect(final JPanel panel){
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

  public boolean isPreview() {
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

  private abstract static class ConfigTreeReader<T> {
    @Nullable
    public T process(@Nullable Element element, @Nullable T context) {
      if (element == null) {
        return null;
      }
      final Element splitterElement = element.getChild("splitter");
      if (splitterElement != null) {
        final Element first = splitterElement.getChild("split-first");
        final Element second = splitterElement.getChild("split-second");
        return processSplitter(splitterElement, first, second, context);
      }

      final Element leaf = element.getChild("leaf");
      if (leaf == null) {
        return null;
      }

      List<Element> fileElements = leaf.getChildren("file");
      final List<Element> children = new ArrayList<>(fileElements.size());

      // trim to EDITOR_TAB_LIMIT, ignoring CLOSE_NON_MODIFIED_FILES_FIRST policy
      int toRemove = fileElements.size() - UISettings.getInstance().getEditorTabLimit();
      for (Element fileElement : fileElements) {
        if (toRemove <= 0 || Boolean.valueOf(fileElement.getAttributeValue(PINNED)).booleanValue()) {
          children.add(fileElement);
        }
        else {
          toRemove--;
        }
      }

      return processFiles(children, context);
    }

    @Nullable
    protected abstract T processFiles(@NotNull List<Element> fileElements, @Nullable T context);
    @Nullable
    protected abstract T processSplitter(@NotNull Element element, @Nullable Element firstChild, @Nullable Element secondChild, @Nullable T context);
  }

  private class UIBuilder extends ConfigTreeReader<JPanel> {

    @Override
    protected JPanel processFiles(@NotNull List<Element> fileElements, final JPanel context) {
      final Ref<EditorWindow> windowRef = new Ref<>();
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> windowRef.set(context == null ? createEditorWindow() : findWindowWith(context)));
      final EditorWindow window = windowRef.get();
      LOG.assertTrue(window != null);
      VirtualFile focusedFile = null;

      for (int i = 0; i < fileElements.size(); i++) {
        final Element file = fileElements.get(i);
        if (i == 0) {
          EditorTabbedContainer tabbedPane = window.getTabbedPane();
          if (tabbedPane != null) {
            try {
              int limit =
                Integer.parseInt(file.getParentElement().getAttributeValue(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY.toString(),
                                                                           String.valueOf(JBTabsImpl.DEFAULT_MAX_TAB_WIDTH)));
              UIUtil.putClientProperty(tabbedPane.getComponent(), JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY, limit);
            }
            catch (NumberFormatException e) {
              //ignore
            }
          }
        }
        try {
          final FileEditorManagerImpl fileEditorManager = getManager();
          Element historyElement = file.getChild(HistoryEntry.TAG);
          final HistoryEntry entry = HistoryEntry.createLight(fileEditorManager.getProject(), historyElement);
          final VirtualFile virtualFile = entry.getFile();
          if (virtualFile == null) throw new InvalidDataException("No file exists: " + entry.getFilePointer().getUrl());
          Document document = ReadAction.compute(() -> virtualFile.isValid() ? FileDocumentManager.getInstance().getDocument(virtualFile) : null);
          final boolean isCurrentInTab = Boolean.valueOf(file.getAttributeValue(CURRENT_IN_TAB)).booleanValue();
          Boolean pin = Boolean.valueOf(file.getAttributeValue(PINNED));
          fileEditorManager.openFileImpl4(window, virtualFile, entry, false, false, pin, i);
          if (isCurrentInTab) {
            focusedFile = virtualFile;
          }
          if (document != null) {
            // This is just to make sure document reference is kept on stack till this point
            // so that document is available for folding state deserialization in HistoryEntry constructor
            // and that document will be created only once during file opening
            document.putUserData(DUMMY_KEY, null);
          }
          updateProgress();
        }
        catch (InvalidDataException e) {
          if (ApplicationManager.getApplication().isUnitTestMode()) {
            LOG.error(e);
          }
        }
      }
      if (focusedFile != null) {
        getManager().addSelectionRecord(focusedFile, window);
        VirtualFile finalFocusedFile = focusedFile;
        UIUtil.invokeLaterIfNeeded(()->{
          EditorWithProviderComposite editor = window.findFileComposite(finalFocusedFile);
          if (editor != null) {
            window.setEditor(editor, true, true);
          }
        });
      }
      return window.myPanel;
    }

    @Override
    protected JPanel processSplitter(@NotNull Element splitterElement, Element firstChild, Element secondChild, final JPanel context) {
      if (context == null) {
        final boolean orientation = "vertical".equals(splitterElement.getAttributeValue("split-orientation"));
        final float proportion = Float.valueOf(splitterElement.getAttributeValue("split-proportion")).floatValue();
        final JPanel firstComponent = process(firstChild, null);
        final JPanel secondComponent = process(secondChild, null);
        final Ref<JPanel> panelRef = new Ref<>();
        UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
          JPanel panel = new JPanel(new BorderLayout());
          panel.setOpaque(false);
          Splitter splitter = new OnePixelSplitter(orientation, proportion, 0.1f, 0.9f);
          panel.add(splitter, BorderLayout.CENTER);
          splitter.setFirstComponent(firstComponent);
          splitter.setSecondComponent(secondComponent);
          panelRef.set(panel);
        });
        return panelRef.get();
      }
      final Ref<JPanel> firstComponent = new Ref<>();
      final Ref<JPanel> secondComponent = new Ref<>();
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
        if (context.getComponent(0) instanceof Splitter) {
          Splitter splitter = (Splitter)context.getComponent(0);
          firstComponent.set((JPanel)splitter.getFirstComponent());
          secondComponent.set((JPanel)splitter.getSecondComponent());
        }
        else {
          firstComponent.set(context);
          secondComponent.set(context);
        }
      });
      process(firstChild, firstComponent.get());
      process(secondChild, secondComponent.get());
      return context;
    }
  }
}
