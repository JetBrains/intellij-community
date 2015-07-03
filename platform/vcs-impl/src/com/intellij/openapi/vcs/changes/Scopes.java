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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class Scopes {
  private final Project myProject;
  private final VcsGuess myGuess;

  private boolean myEverythingDirty;
  private final Map<AbstractVcs, VcsDirtyScopeImpl> myScopes;

  public Scopes(final Project project, final VcsGuess guess) {
    myProject = project;
    myGuess = guess;
    myScopes = new HashMap<AbstractVcs, VcsDirtyScopeImpl>();
  }

  private void markEverythingDirty() {
    myScopes.clear();
    myEverythingDirty = true;
    final DirtBuilder builder = new DirtBuilder(myGuess);
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    final List<VcsDirectoryMapping> mappings = vcsManager.getDirectoryMappings();

    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    for (VcsDirectoryMapping mapping : mappings) {
      if (mapping.isDefaultMapping()) {
        DefaultVcsRootPolicy.getInstance(myProject).markDefaultRootsDirty(builder, myGuess);
      } else {
        if (mapping.getVcs() != null) {
          final String vcsName = mapping.getVcs();
          final AbstractVcs vcs = vcsManager.findVcsByName(vcsName);
          final VirtualFile file = lfs.findFileByPath(mapping.getDirectory());
          if (vcs != null && file != null) {
            builder.addDirtyDirRecursively(vcs, VcsUtil.getFilePath(file));
          }
        }
      }
    }

    DefaultVcsRootPolicy.getInstance(myProject).markDefaultRootsDirty(builder, myGuess);
    takeDirt(builder);
  }

  public void takeDirt(final DirtBuilderReader dirt) {
    if (dirt.isEverythingDirty()) {
      markEverythingDirty();
      return;
    }

    MultiMap<AbstractVcs, FilePath> files = dirt.getFilesForVcs();
    MultiMap<AbstractVcs, FilePath> dirs = dirt.getDirsForVcs();

    Set<AbstractVcs> keys = ContainerUtil.newHashSet(files.keySet());
    keys.addAll(dirs.keySet());
    for (AbstractVcs key : keys) {
      getScope(key).addDirtyData(dirs.get(key), files.get(key));
    }
  }

  public void addDirtyDirRecursively(@NotNull final AbstractVcs vcs, @NotNull final FilePath newcomer) {
    getScope(vcs).addDirtyDirRecursively(newcomer);
  }

  public void addDirtyFile(@NotNull final AbstractVcs vcs, @NotNull final FilePath newcomer) {
    getScope(vcs).addDirtyFile(newcomer);
  }

  @NotNull
  public VcsInvalidated retrieveAndClear() {
    final ArrayList<VcsDirtyScope> scopesList = new ArrayList<VcsDirtyScope>(myScopes.values());
    final VcsInvalidated result = new VcsInvalidated(scopesList, myEverythingDirty);
    myEverythingDirty = false;
    myScopes.clear();
    return result;
  }

  private VcsDirtyScopeImpl getScope(final AbstractVcs vcs) {
    VcsDirtyScopeImpl scope = myScopes.get(vcs);
    if (scope == null) {
      scope = new VcsDirtyScopeImpl(vcs, myProject);
      myScopes.put(vcs, scope);
    }
    return scope;
  }
}
