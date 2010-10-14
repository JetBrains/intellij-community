/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockContainerFactory;
import com.intellij.ui.docking.DockableContent;

import javax.swing.*;
import java.util.concurrent.CopyOnWriteArraySet;

class DockableEditorTabbedContainer implements DockContainerFactory, DockContainer {

  private EditorsSplitters mySplitters;
  private Project myProject;

  private CopyOnWriteArraySet<Listener> myListeners = new CopyOnWriteArraySet<Listener>();

  DockableEditorTabbedContainer(Project project) {
    myProject = project;
  }

  @Override
  public DockContainer createContainer() {
    mySplitters = new EditorsSplitters((FileEditorManagerImpl)FileEditorManager.getInstance(myProject)) {
      @Override
      protected void afterFileClosed(VirtualFile file) {
        fireContentClosed(file);
      }

      @Override
      protected void afterFileOpen(VirtualFile file) {
        fireContentOpen(file);
      }
    };
    mySplitters.createCurrentWindow();
    return this;
  }

  private void fireContentClosed(VirtualFile file) {
    for (Listener each : myListeners) {
      each.contentRemoved(file);
    }
  }

  private void fireContentOpen(VirtualFile file) {
    for (Listener each : myListeners) {
      each.contentAdded(file);
    }
  }

  @Override
  public RelativeRectangle getAcceptArea() {
    return new RelativeRectangle(mySplitters);
  }

  @Override
  public boolean canAccept(DockableContent content) {
    return content instanceof EditorTabbedContainer.MyDragOutDelegate.DockableEditor;
  }

  @Override
  public void add(DockableContent content, RelativePoint dropTarget) {
    VirtualFile file = ((EditorTabbedContainer.MyDragOutDelegate.DockableEditor)content).getFile();
    EditorWindow currentWindow = mySplitters.getCurrentWindow();
    ((FileEditorManagerImpl)FileEditorManagerEx.getInstanceEx(myProject)).openFileImpl2(currentWindow, file, true);
  }

  @Override
  public JComponent getComponent() {
    return mySplitters;
  }

  public EditorsSplitters getSplitters() {
    return mySplitters;
  }

  public void close(VirtualFile file) {
    mySplitters.getCurrentWindow().closeFile(file);
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
    return mySplitters.getOpenFiles().length == 0;
  }
}
