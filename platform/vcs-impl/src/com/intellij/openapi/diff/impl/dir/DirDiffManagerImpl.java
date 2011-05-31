/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.ide.diff.JarFileDiffElement;
import com.intellij.ide.diff.VirtualFileDiffElement;
import com.intellij.openapi.diff.DirDiffManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffManagerImpl extends DirDiffManager {
  private final Project myProject;

  public DirDiffManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public void showDiff(@NotNull final DiffElement dir1, @NotNull final DiffElement dir2, final DirDiffSettings settings) {
    final DirDiffTableModel model = new DirDiffTableModel(myProject, dir1, dir2, settings);
    if (settings.showInFrame) {
      new DirDiffFrame(myProject, model).show();
    } else {
      final DirDiffDialog dirDiffDialog = new DirDiffDialog(myProject, model);
      if (myProject == null || myProject.isDefault()) {
        dirDiffDialog.setModal(true);
      }
      dirDiffDialog.show();
    }
  }

  @Override
  public boolean canShow(@NotNull DiffElement dir1, @NotNull DiffElement dir2) {
    return dir1.isContainer() && dir2.isContainer();
  }

  @Override
  public DiffElement createDiffElement(Object obj) {
    //TODO make EP
    if (obj instanceof VirtualFile) {
      final VirtualFile file = (VirtualFile)obj;
      return JarFileSystem.PROTOCOL.equalsIgnoreCase(file.getExtension())
        ? new JarFileDiffElement(file) : new VirtualFileDiffElement(file);
    }
    return null;
  }
}
