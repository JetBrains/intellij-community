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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockableContent;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CopyOnWriteArraySet;

public class DockableEditorTabbedContainer implements DockContainer.Persistent {

  private final EditorsSplitters mySplitters;
  private final Project myProject;

  private final CopyOnWriteArraySet<Listener> myListeners = new CopyOnWriteArraySet<>();

  private JBTabs myCurrentOver;
  private Image myCurrentOverImg;
  private TabInfo myCurrentOverInfo;

  private boolean myDisposeWhenEmpty;

  private boolean myWasEverShown;

  DockableEditorTabbedContainer(Project project) {
    this(project, null, true);
  }

  DockableEditorTabbedContainer(Project project, @Nullable EditorsSplitters splitters, boolean disposeWhenEmpty) {
    myProject = project;
    mySplitters = splitters;
    myDisposeWhenEmpty = disposeWhenEmpty;
  }

  @Override
  public String getDockContainerType() {
    return DockableEditorContainerFactory.TYPE;
  }

  @Override
  public Element getState() {
    Element editors = new Element("state");
    mySplitters.writeExternal(editors);
    return editors;
  }

  void fireContentClosed(VirtualFile file) {
    for (Listener each : myListeners) {
      each.contentRemoved(file);
    }
  }

  void fireContentOpen(VirtualFile file) {
    for (Listener each : myListeners) {
      each.contentAdded(file);
    }
  }

  @Override
  public RelativeRectangle getAcceptArea() {
    return new RelativeRectangle(mySplitters);
  }
  
  public RelativeRectangle getAcceptAreaFallback() {
    JRootPane root = mySplitters.getRootPane();
    return root != null ? new RelativeRectangle(root) : new RelativeRectangle(mySplitters);
  }

  @NotNull
  @Override
  public ContentResponse getContentResponse(@NotNull DockableContent content, RelativePoint point) {
    return getTabsAt(content, point) != null ? ContentResponse.ACCEPT_MOVE : ContentResponse.DENY;
  }

  @Nullable
  private JBTabs getTabsAt(DockableContent content, RelativePoint point) {
    if (content instanceof EditorTabbedContainer.DockableEditor) {
      JBTabs targetTabs = mySplitters.getTabsAt(point);
      if (targetTabs != null) {
        return targetTabs;
      } else {
        EditorWindow wnd = mySplitters.getCurrentWindow();
        if (wnd != null) {
          EditorTabbedContainer tabs = wnd.getTabbedPane();
          if (tabs != null) {
            return tabs.getTabs();
          }
        } else {
          EditorWindow[] windows = mySplitters.getWindows();
          for (EditorWindow each : windows) {
            if (each.getTabbedPane() != null && each.getTabbedPane().getTabs() != null) {
              return each.getTabbedPane().getTabs();
            }
          }
        }
      }
    }

    return null;
  }

  @Override
  public void add(@NotNull DockableContent content, RelativePoint dropTarget) {
    EditorWindow window = null;
    if (myCurrentOver != null) {
      final DataProvider provider = myCurrentOver.getDataProvider();
      if (provider != null) {
        window = EditorWindow.DATA_KEY.getData(provider);
      }
    }

    final EditorTabbedContainer.DockableEditor dockableEditor = (EditorTabbedContainer.DockableEditor)content;
    VirtualFile file = dockableEditor.getFile();


    if (window == null || window.isDisposed()) {
      window = mySplitters.getOrCreateCurrentWindow(file);
    }


    if (myCurrentOver != null) {
      int index = ((JBTabsImpl)myCurrentOver).getDropInfoIndex();
      file.putUserData(EditorWindow.INITIAL_INDEX_KEY, index);
    }

    ((FileEditorManagerImpl)FileEditorManagerEx.getInstanceEx(myProject)).openFileImpl2(window, file, true);
    window.setFilePinned(file, dockableEditor.isPinned());
  }

  @Override
  public Image startDropOver(@NotNull DockableContent content, RelativePoint point) {
    return null;
  }

  @Override
  public Image processDropOver(@NotNull DockableContent content, RelativePoint point) {
    JBTabs current = getTabsAt(content, point);

    if (myCurrentOver != null && myCurrentOver != current) {
      resetDropOver(content);
    }

    if (myCurrentOver == null && current != null) {
      myCurrentOver = current;
      Presentation presentation = content.getPresentation();
      myCurrentOverInfo = new TabInfo(new JLabel("")).setText(presentation.getText()).setIcon(presentation.getIcon());
      myCurrentOverImg = myCurrentOver.startDropOver(myCurrentOverInfo, point);
    }

    if (myCurrentOver != null) {
      myCurrentOver.processDropOver(myCurrentOverInfo, point);
    }

    return myCurrentOverImg;
  }

  @Override
  public void resetDropOver(@NotNull DockableContent content) {
    if (myCurrentOver != null) {
      myCurrentOver.resetDropOver(myCurrentOverInfo);
      myCurrentOver = null;
      myCurrentOverInfo = null;
      myCurrentOverImg = null;
    }
  }

  @Override
  public JComponent getContainerComponent() {
    return mySplitters;
  }

  public EditorsSplitters getSplitters() {
    return mySplitters;
  }

  public void close(VirtualFile file) {
    mySplitters.closeFile(file, false);
  }

  @Override
  public void closeAll() {
    VirtualFile[] files = mySplitters.getOpenFiles();
    for (VirtualFile each : files) {
      close(each);
    }
  }

  @Override
  public void addListener(final Listener listener, Disposable parent) {
    myListeners.add(listener);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        myListeners.remove(listener);
      }
    });
  }

  @Override
  public boolean isEmpty() {
    return mySplitters.isEmptyVisible();
  }

  @Override
  public void dispose() {
    closeAll();
  }

  @Override
  public boolean isDisposeWhenEmpty() {
    return myDisposeWhenEmpty;
  }

  @Override
  public void showNotify() {
    if (!myWasEverShown) {
      myWasEverShown = true;
      getSplitters().openFiles();
    }
  }

  @Override
  public void hideNotify() {
  }
}
