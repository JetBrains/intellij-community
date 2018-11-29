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
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MappingsToRoots {
  private final NewMappings myMappings;
  private final Project myProject;

  public MappingsToRoots(final NewMappings mappings, final Project project) {
    myMappings = mappings;
    myProject = project;
  }

  @NotNull
  public VirtualFile[] getRootsUnderVcs(@NotNull AbstractVcs vcs) {
    final List<VirtualFile> result = new ArrayList<>(myMappings.getMappingsAsFilesUnderVcs(vcs));

    Collections.sort(result, FilePathComparator.getInstance());
    if (!vcs.allowsNestedRoots()) {
      ApplicationManager.getApplication().runReadAction(() -> {
        final FileIndexFacade facade = ServiceManager.getService(myProject, FileIndexFacade.class);
        int i = 1;
        while (i < result.size()) {
          final VirtualFile previous = result.get(i - 1);
          final VirtualFile current = result.get(i);
          if (facade.isValidAncestor(previous, current)) {
            result.remove(i);
          }
          else {
            i++;
          }
        }
      });
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  // not only set mappings, but include all modules inside: modules might have different settings
  public List<VirtualFile> getDetailedVcsMappings(@NotNull AbstractVcs vcs) {
    final List<VirtualFile> result = new ArrayList<>();
    boolean addInnerModules = false;

    List<VcsDirectoryMapping> directoryMappings = myMappings.getDirectoryMappings(vcs.getName());
    for (VcsDirectoryMapping mapping : directoryMappings) {
      if (mapping.isDefaultMapping()) {
        addInnerModules = true;
      }
      else {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(mapping.getDirectory());
        if (file != null) result.add(file);
      }
    }

    Collections.sort(result, FilePathComparator.getInstance());
    if (addInnerModules) {
      Collection<VirtualFile> modules = DefaultVcsRootPolicy.getInstance(myProject).getDefaultVcsRoots();
      Collection<VirtualFile> modulesUnderVcs = ContainerUtil.filter(modules, file -> {
        if (!file.isDirectory()) return false;
        NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
        return root != null && vcs.equals(root.vcs);
      });

      ApplicationManager.getApplication().runReadAction(() -> {
        final FileIndexFacade facade = ServiceManager.getService(myProject, FileIndexFacade.class);
        Iterator<VirtualFile> iterator = modulesUnderVcs.iterator();
        while (iterator.hasNext()) {
          final VirtualFile module = iterator.next();
          boolean included = false;
          for (VirtualFile root : result) {
            if (facade.isValidAncestor(root, module)) {
              included = true;
              break;
            }
          }
          if (!included) {
            iterator.remove();
          }
        }
      });
      result.addAll(modulesUnderVcs);
    }
    return result;
  }
}
