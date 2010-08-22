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
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.LocalFileSystem;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
* @author irengrig
*/
public class FileWatchRequestModifier implements Runnable {
  private final Project myProject;
  private final NewMappings myNewMappings;
  private final Map<VcsDirectoryMapping, LocalFileSystem.WatchRequest> myDirectoryMappingWatches;
  private final LocalFileSystem myLfs;

  public FileWatchRequestModifier(final Project project, final NewMappings newMappings, final LocalFileSystem localFileSystem) {
    myLfs = localFileSystem;
    myProject = project;
    myNewMappings = newMappings;
    myDirectoryMappingWatches = new HashMap<VcsDirectoryMapping, LocalFileSystem.WatchRequest>();
  }

  @Override
  public void run() {
    if ((! myProject.isInitialized()) || myProject.isDisposed()) return;
    final List<VcsDirectoryMapping> copy = myNewMappings.getDirectoryMappings();

    final List<VcsDirectoryMapping> added = new LinkedList<VcsDirectoryMapping>(copy);
    added.removeAll(myDirectoryMappingWatches.keySet());

    final List<VcsDirectoryMapping> deleted = new LinkedList<VcsDirectoryMapping>(myDirectoryMappingWatches.keySet());
    deleted.removeAll(copy);

    for (VcsDirectoryMapping mapping : added) {
      if (mapping.isDefaultMapping()) continue;
      final LocalFileSystem.WatchRequest watchRequest = myLfs.addRootToWatch(mapping.getDirectory(), true);
      myDirectoryMappingWatches.put(mapping, watchRequest);
    }
    for (VcsDirectoryMapping mapping : deleted) {
      if (mapping.isDefaultMapping()) continue;
      final LocalFileSystem.WatchRequest removed = myDirectoryMappingWatches.remove(mapping);
      if (removed != null) {
        myLfs.removeWatchedRoot(removed);
      }
    }
  }
}
