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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockContainerFactory;
import com.intellij.ui.docking.DockManager;
import org.jdom.Element;

public class DockableEditorContainerFactory implements DockContainerFactory.Persistent {

  public static final String TYPE = "file-editors";

  private Project myProject;
  private FileEditorManagerImpl myFileEditorManager;
  private DockManager myDockManager;

  public DockableEditorContainerFactory(Project project, FileEditorManagerImpl fileEditorManager, DockManager dockManager) {
    this.myProject = project;
    myFileEditorManager = fileEditorManager;
    myDockManager = dockManager;
  }

  @Override
  public DockContainer createContainer() {
    return createContainer(false);
  }

  private DockContainer createContainer(boolean loadingState) {
    final Ref<DockableEditorTabbedContainer> container = new Ref<DockableEditorTabbedContainer>();
    EditorsSplitters splitters = new EditorsSplitters(myFileEditorManager, myDockManager, false) {
      @Override
      protected void afterFileClosed(VirtualFile file) {
        container.get().fireContentClosed(file);
      }

      @Override
      protected void afterFileOpen(VirtualFile file) {
        container.get().fireContentOpen(file);
      }

      @Override
      protected IdeFrame getFrame(Project project) {
        return DockManager.getInstance(project).getIdeFrame(container.get());
      }
    };
    if (!loadingState) {
      splitters.createCurrentWindow();
    }
    container.set(new DockableEditorTabbedContainer(myProject, myDockManager, splitters, true));
    return container.get();
  }

  @Override
  public DockContainer loadContainerFrom(Element element) {
    DockableEditorTabbedContainer container = (DockableEditorTabbedContainer)createContainer(true);
    container.getSplitters().readExternal(element.getChild("state"));
    return container;
  }

  @Override
  public void dispose() {
  }
}
