// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.ui.tabs.JBTabsEx;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.update.Activatable;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CopyOnWriteArraySet;

public final class DockableEditorTabbedContainer implements DockContainer.Persistent, Activatable {
  private final EditorsSplitters mySplitters;
  private final Project myProject;

  private final CopyOnWriteArraySet<Listener> myListeners = new CopyOnWriteArraySet<>();

  private JBTabs myCurrentOver;
  private Image myCurrentOverImg;
  private TabInfo myCurrentOverInfo;

  private final boolean myDisposeWhenEmpty;

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

  void fireContentClosed(@NotNull VirtualFile file) {
    for (Listener each : myListeners) {
      each.contentRemoved(file);
    }
  }

  void fireContentOpen(@NotNull VirtualFile file) {
    for (Listener each : myListeners) {
      each.contentAdded(file);
    }
  }

  @Override
  public @NotNull RelativeRectangle getAcceptArea() {
    return new RelativeRectangle(mySplitters);
  }

  @Override
  public @NotNull RelativeRectangle getAcceptAreaFallback() {
    JRootPane root = mySplitters.getRootPane();
    return root != null ? new RelativeRectangle(root) : new RelativeRectangle(mySplitters);
  }

  @Override
  public @NotNull ContentResponse getContentResponse(@NotNull DockableContent content, RelativePoint point) {
    return getTabsAt(content, point) != null ? ContentResponse.ACCEPT_MOVE : ContentResponse.DENY;
  }

  private @Nullable JBTabs getTabsAt(DockableContent<?> content, RelativePoint point) {
    if (!(content instanceof EditorTabbedContainer.DockableEditor)) {
      return null;
    }

    JBTabs targetTabs = mySplitters.getTabsAt(point);
    if (targetTabs != null) {
      return targetTabs;
    }
    else {
      EditorWindow window = mySplitters.getCurrentWindow();
      if (window != null) {
        return window.getTabbedPane().getTabs();
      }
      else {
        EditorWindow[] windows = mySplitters.getWindows();
        for (EditorWindow each : windows) {
          if (each.getTabbedPane().getTabs() != null) {
            return each.getTabbedPane().getTabs();
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
      int index = ((JBTabsEx)myCurrentOver).getDropInfoIndex();
      file.putUserData(EditorWindow.INITIAL_INDEX_KEY, index);
    }

    ((FileEditorManagerImpl)FileEditorManagerEx.getInstanceEx(myProject)).openFileImpl2(window, file, true);
    window.setFilePinned(file, dockableEditor.isPinned());
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

  public void close(@NotNull VirtualFile file) {
    mySplitters.closeFile(file, false);
  }

  @Override
  public void closeAll() {
    for (VirtualFile each : mySplitters.getOpenFileList()) {
      close(each);
    }
  }

  @Override
  public void addListener(@NotNull Listener listener, Disposable parent) {
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
}
