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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;

import java.util.*;

public class RootsCalculator {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.committed.RootsCalculator");
  private final Project myProject;
  private final AbstractVcs myVcs;
  private final ProjectLevelVcsManager myPlManager;
  private VirtualFile[] myContentRoots;
  private final RepositoryLocationCache myLocationCache;

  public RootsCalculator(final Project project, final AbstractVcs vcs, final RepositoryLocationCache locationCache) {
    myProject = project;
    myLocationCache = locationCache;
    myPlManager = ProjectLevelVcsManager.getInstance(myProject);
    myVcs = vcs;
  }

  public Map<VirtualFile, RepositoryLocation> getRoots() {
    myContentRoots = myPlManager.getRootsUnderVcs(myVcs);

    List<VirtualFile> roots = new ArrayList<>();
    final List<VcsDirectoryMapping> mappings = myPlManager.getDirectoryMappings(myVcs);
    for (VcsDirectoryMapping mapping : mappings) {
      if (mapping.isDefaultMapping()) {
        if (myVcs.equals(myPlManager.getVcsFor(myProject.getBaseDir()))) {
          roots.add(myProject.getBaseDir());
        }
      }
      else {
        VirtualFile newFile = LocalFileSystem.getInstance().findFileByPath(mapping.getDirectory());
        if (newFile == null) {
          newFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(mapping.getDirectory());
        }
        if (newFile != null) {
          roots.add(newFile);
        }
        else {
          LOG.info("Can not file virtual file for root: " + mapping.getDirectory());
        }
      }
    }
    ContainerUtil.addAll(roots, myContentRoots);
    final Map<VirtualFile, RepositoryLocation> result = new HashMap<>();
    for (Iterator<VirtualFile> iterator = roots.iterator(); iterator.hasNext();) {
      final VirtualFile vf = iterator.next();
      final RepositoryLocation location = myLocationCache.getLocation(myVcs, VcsUtil.getFilePath(vf), false);
      if (location != null) {
        result.put(vf, location);
      }
      else {
        iterator.remove();
      }
    }
    roots = myVcs.filterUniqueRoots(roots, IntoSelfVirtualFileConvertor.getInstance());
    result.keySet().retainAll(roots);

    logRoots(roots);
    return result;
  }

  private void logRoots(final List<VirtualFile> roots) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Roots for committed changes load:\n");
      for (VirtualFile root : roots) {
        LOG.debug(root.getPath() + ", ");
      }
    }
  }

  public VirtualFile[] getContentRoots() {
    return myContentRoots;
  }
}
