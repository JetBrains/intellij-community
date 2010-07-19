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

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.impl.text.FileDropHandler;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ArrayListSet;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.*;
import java.util.List;


/**
 * Author: msk
 */
public final class EditorsSplitters extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.EditorsSplitters");
  private EditorWindow myCurrentWindow;
  private VirtualFile myCurrentFile;
  private final FileEditorManagerImpl myManager;
  private Element mySplittersElement;  // temporarily used during initialization
  private int myInsideChange = 0;
  private final MyFocusWatcher myFocusWatcher;
  private EditorWithProviderComposite myCurrentSelectedEditor;
  private final Alarm myIconUpdaterAlarm = new Alarm();

  public EditorsSplitters(final FileEditorManagerImpl manager) {
    super(new BorderLayout());
    setOpaque(true);
    setBackground(Color.GRAY);
    myManager = manager;
    myFocusWatcher = new MyFocusWatcher();
    setFocusTraversalPolicy(new MyFocusTraversalPolicy());
    setTransferHandler(new MyTransferHandler());
    clear();
  }

  public FileEditorManagerImpl getManager() {
    return myManager;
  }

  public void clear() {
    removeAll();
    myWindows.clear();
    setCurrentWindow(null);
    myCurrentFile = null;
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
    final FileEditor[] editors = composite.getEditors();
    final Element fileElement = new Element("file");
    fileElement.setAttribute("leaf-file-name", file.getName()); // TODO: all files
    final FileEditorState[] states = new FileEditorState[editors.length];
    for (int j = 0; j < states.length; j++) {
      states[j] = editors[j].getState(FileEditorStateLevel.FULL);
      LOG.assertTrue(states[j] != null);
    }
    final int selectedProviderIndex = ArrayUtil.find(editors, composite.getSelectedEditor());
    LOG.assertTrue(selectedProviderIndex != -1);
    final FileEditorProvider[] providers = composite.getProviders();
    final HistoryEntry entry = new HistoryEntry(file, providers, states, providers[selectedProviderIndex]); // TODO
    entry.writeExternal(fileElement, getManager().getProject());
    fileElement.setAttribute("pinned",         Boolean.toString(pinned));
    fileElement.setAttribute("current",        Boolean.toString(composite.equals (getManager ().getLastSelected ())));
    fileElement.setAttribute("current-in-tab", Boolean.toString(composite.equals (selectedEditor)));
    res.addContent(fileElement);
  }

  public void openFiles() {
    if (mySplittersElement != null) {
      final JPanel comp = readExternalPanel(mySplittersElement, getTopPanel());
      if (comp != null) {
        removeAll();
        add(comp, BorderLayout.CENTER);
        final EditorComposite lastSelected = getManager().getLastSelected();
        if(lastSelected != null)  {
          getManager().openFileImpl3(myCurrentWindow, lastSelected.getFile(), true, null, true);
          //lastSelected.getComponent().requestFocus();
          //ToolWindowManager.getInstance(getManager().myProject).activateEditorComponent();
        }
        mySplittersElement = null;
      }
    }
  }

  public void readExternal(final Element element) {
    mySplittersElement = element;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public JPanel readExternalPanel(final Element element, @Nullable JPanel panel) {
    final Element splitterElement = element.getChild("splitter");
    if (splitterElement != null) {
      LOG.info("splitter");
      final boolean orientation = "vertical".equals(splitterElement.getAttributeValue("split-orientation"));
      final float proportion = Float.valueOf(splitterElement.getAttributeValue("split-proportion")).floatValue();
      final Element first = splitterElement.getChild("split-first");
      final Element second = splitterElement.getChild("split-second");

      Splitter splitter;
      if (panel == null) {
        panel = new JPanel(new BorderLayout());
        splitter = new Splitter(orientation, proportion, 0.1f, 0.9f);
        panel.add(splitter, BorderLayout.CENTER);
        splitter.setFirstComponent(readExternalPanel(first, null));
        splitter.setSecondComponent(readExternalPanel(second, null));
      } else if (panel.getComponent(0) instanceof Splitter) {
        splitter = (Splitter)panel.getComponent(0);
        readExternalPanel(first, (JPanel)splitter.getFirstComponent());
        readExternalPanel(second, (JPanel)splitter.getSecondComponent());
      } else {
        readExternalPanel(first, panel);
        readExternalPanel(second, panel);
      }
      return panel;
    }
    final Element leaf = element.getChild("leaf");
    if (leaf != null) {
      EditorWindow window;
      if (panel == null) {
        window = new EditorWindow(this);
      } else {
        window = findWindowWith(panel);
      }
      try {
        //noinspection unchecked
        final List<Element> children = leaf.getChildren("file");
        VirtualFile currentFile = null;
        for (final Element file : children) {
          final HistoryEntry entry = new HistoryEntry(getManager().getProject(), file.getChild(HistoryEntry.TAG));
          boolean isCurrent = Boolean.valueOf(file.getAttributeValue("current")).booleanValue();
          getManager().openFileImpl3(window, entry.myFile, false, entry, isCurrent);
          if (getManager().isFileOpen(entry.myFile)) {
            window.setFilePinned(entry.myFile, Boolean.valueOf(file.getAttributeValue("pinned")).booleanValue());
            if (Boolean.valueOf(file.getAttributeValue("current-in-tab")).booleanValue()) {
              currentFile = entry.myFile;
            }
            if (isCurrent) {
              setCurrentWindow(window, false);
            }
          }
        }
        if (currentFile != null) {
          final EditorComposite editor = window.findFileComposite(currentFile);
          if (editor != null) {
            window.setSelectedEditor(editor, true);
          }
        }
      }
      catch (InvalidDataException e) {
        // OK
      }
      return window.myPanel;
    }
    return null;
  }

  @NotNull public VirtualFile[] getOpenFiles() {
    final ArrayListSet<VirtualFile> files = new ArrayListSet<VirtualFile>();
    for (final EditorWindow myWindow : myWindows) {
      final EditorWithProviderComposite[] editors = myWindow.getEditors();
      for (final EditorWithProviderComposite editor : editors) {
        files.add(editor.getFile());
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
        if (virtualFiles[i] == currentFile) {
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

  void updateFileIcon(@NotNull final VirtualFile file) {
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
      window.setWaveColor(index, getManager().isProblem(file) ? Color.red : null);
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

  public void updateFileName(final VirtualFile file) {
    final EditorWindow[] windows = getWindows();
    for (int i = 0; i != windows.length; ++ i) {
      windows [i].updateFileName(file);
    }
  }

  public boolean isInsideChange() {
    return myInsideChange > 0;
  }

  private void setCurrentWindow(final EditorWindow currentWindow) {
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

  private final class MyFocusTraversalPolicy extends IdeFocusTraversalPolicy {
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

  private void createCurrentWindow() {
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
  public void setCurrentWindow(final EditorWindow window, final boolean requestFocus) {
    final EditorWithProviderComposite oldEditor = myCurrentSelectedEditor;
    final EditorWithProviderComposite newEditor = window != null? window.getSelectedEditor() : null;
    try {
      getManager().fireSelectionChanged(oldEditor, newEditor);
    }
    finally {
      setCurrentWindow(window);
      myCurrentSelectedEditor = newEditor;
    }

    getManager().updateFileName(window == null ? null : window.getSelectedFile());

    if (window != null) {
      final EditorWithProviderComposite selectedEditor = myCurrentWindow.getSelectedEditor();
      if (selectedEditor != null) {
        boolean alreadyFocused = false;
        final JComponent comp = selectedEditor.getComponent();
        final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (owner != null && comp != null) {
          alreadyFocused = owner == comp || SwingUtilities.isDescendingFrom(owner, comp);
        }

        if (requestFocus && !alreadyFocused) {
          IdeFocusManager.getInstance(myManager.getProject()).requestFocus(comp, requestFocus);
        }
      }
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

  final Set<EditorWindow> myWindows = new ArrayListSet<EditorWindow>();

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

  private final class MyFocusWatcher extends FocusWatcher {
    protected void focusedComponentChanged(final Component component, final AWTEvent cause) {
      EditorWindow newWindow = null;
      VirtualFile newFile = null;

      if (component != null) {
        newWindow = findWindowWith(component);
        if (newWindow != null) {
          newFile = newWindow.getSelectedFile();
        }
      }

      boolean changed = !Comparing.equal(newWindow, myCurrentWindow) || !Comparing.equal(newFile, myCurrentFile);

      myCurrentFile = newFile;
      setCurrentWindow(newWindow);

      if (changed) {
        setCurrentWindow(newWindow, false);
      }
    }
  }

  public void runChange(Runnable change) {
    myInsideChange++;
    try {
      change.run();
    }
    finally {
      myInsideChange--;
    }
  }

  private final class MyTransferHandler extends TransferHandler {
    private final FileDropHandler myFileDropHandler = new FileDropHandler();

    @Override
    public boolean importData(JComponent comp, Transferable t) {
      if (myFileDropHandler.canHandleDrop(t.getTransferDataFlavors())) {
        myFileDropHandler.handleDrop(t, myManager.getProject());
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
