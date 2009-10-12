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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class VcsDirtyScopeVfsListener extends VirtualFileAdapter implements ApplicationComponent {
  private final ProjectLocator myProjectLocator;

  public VcsDirtyScopeVfsListener() {
    myProjectLocator = ProjectLocator.getInstance();
  }

  @NotNull
  public String getComponentName() {
    return VcsDirtyScopeVfsListener.class.getName();
  }

  public void initComponent() {
    VirtualFileManager.getInstance().addVirtualFileListener(this, ApplicationManager.getApplication());
  }

  public void disposeComponent() {
    VirtualFileManager.getInstance().removeVirtualFileListener(this);
  }

  @Nullable
  VcsDirtyScopeManager getManager(final VirtualFileEvent event) {
    final Project project = myProjectLocator.guessProjectForFile(event.getFile());
    if (project == null) return null;
    return VcsDirtyScopeManager.getInstance(project);
  }

  @Override
  public void contentsChanged(VirtualFileEvent event) {
    final VcsDirtyScopeManager manager = getManager(event);
    if (manager != null) {
      manager.fileDirty(event.getFile());
    }
  }

  @Override
  public void propertyChanged(VirtualFilePropertyEvent event) {
    final VcsDirtyScopeManager manager = getManager(event);
    if (manager != null) {
      if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
        VirtualFile renamed = event.getFile();
        if (renamed.getParent() != null) {
          renamed = renamed.getParent();
        }
        dirtyFileOrDir(manager, renamed);
      }
      else {
        manager.fileDirty(event.getFile());
      }
    }
  }

  private static void dirtyFileOrDir(@NotNull final VcsDirtyScopeManager manager, final VirtualFile file) {
    if (file.isDirectory()) {
        manager.dirDirtyRecursively(file);
      }
      else {
        manager.fileDirty(file);
      }
  }

  @Override
  public void fileCreated(VirtualFileEvent event) {
    final VcsDirtyScopeManager manager = getManager(event);
    if (manager != null) {
      manager.fileDirty(event.getFile());
    }
  }

  @Override
  public void beforePropertyChange(VirtualFilePropertyEvent event) {
    final VcsDirtyScopeManager manager = getManager(event);
    if (manager != null) {
      manager.fileDirty(event.getFile());
    }
  }

  @Override
  public void beforeFileDeletion(VirtualFileEvent event) {
    if (!event.getFile().isInLocalFileSystem()) return;

    final VcsDirtyScopeManager manager = getManager(event);
    if (manager != null) {
      // need to keep track of whether the deleted file was a directory
      final boolean directory = event.getFile().isDirectory();
      final FilePathImpl path = new FilePathImpl(new File(event.getFile().getPath()), directory);
      if (directory) {
        manager.dirDirtyRecursively(path);   // IDEADEV-12752
      }
      else {
        manager.fileDirty(path);
      }
    }
  }

  @Override
  public void beforeFileMovement(VirtualFileMoveEvent event) {
    final VcsDirtyScopeManager manager = getManager(event);
    if (manager != null) {
      // need to create FilePath explicitly without referring to VirtualFile because otherwise the FilePath
      // will reference the path after the move
      manager.fileDirty(new FilePathImpl(new File(event.getFile().getPath()), event.getFile().isDirectory()));
    }
  }

  @Override
  public void fileMoved(VirtualFileMoveEvent event) {
    final VcsDirtyScopeManager manager = getManager(event);
    if (manager != null) {
      dirtyFileOrDir(manager, event.getFile());
    }
  }
}
