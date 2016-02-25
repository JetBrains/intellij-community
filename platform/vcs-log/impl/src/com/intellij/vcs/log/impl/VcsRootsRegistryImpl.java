/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.vcs.log.util.PersistentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class VcsRootsRegistryImpl implements VcsRootsRegistry, Disposable {
  @NotNull private static final Logger LOG = Logger.getInstance(VcsRootsRegistry.class);
  @NotNull private static final String LOG_KIND = "roots";
  @NotNull private final PersistentStringEnumerator myEnumerator;
  private static final int VERSION = 0;

  public VcsRootsRegistryImpl(@NotNull final Project project) {
    myEnumerator = createEnumerator(project);
    Disposer.register(project, this);
  }

  @NotNull
  private static PersistentStringEnumerator createEnumerator(@NotNull Project project) {
    try {
      return PersistentUtil
        .createPersistentStringEnumerator(LOG_KIND, project.getName() + "." + project.getBaseDir().getPath().hashCode(), VERSION);
    }
    catch (IOException e) {
      throw new RuntimeException("Can not create persistent storage for vcs roots.", e);
    }
  }

  @Override
  public void dispose() {
    try {
      myEnumerator.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Override
  public int getId(@NotNull VirtualFile root) {
    try {
      return myEnumerator.enumerate(root.getPath());
    }
    catch (IOException e) {
      LOG.error(e);
      throw new RuntimeException(e); // to be dealt with in rr/julia/persistenthashmap branch
    }
  }

  @Override
  @Nullable
  public VirtualFile getRootById(int id) {
    try {
      String path = myEnumerator.valueOf(id);
      if (path == null) throw new RuntimeException("Can not find path by id " + id);
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
      if (file == null) {
        LOG.info("Can not find file by path " + path);
        return null;
      }
      return file;
    }
    catch (IOException e) {
      LOG.error(e);
      throw new RuntimeException(e); // to be dealt with in rr/julia/persistenthashmap branch
    }
  }
}
