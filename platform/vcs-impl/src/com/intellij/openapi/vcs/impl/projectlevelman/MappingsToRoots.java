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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MappingsToRoots {
  private final NewMappings myMappings;
  private final Project myProject;

  public MappingsToRoots(@NotNull NewMappings mappings, @NotNull Project project) {
    myMappings = mappings;
    myProject = project;
  }

  @NotNull
  public VirtualFile[] getRootsUnderVcs(@NotNull AbstractVcs vcs) {
    List<VirtualFile> mappings = new ArrayList<>(myMappings.getMappingsAsFilesUnderVcs(vcs));

    final AbstractVcs.RootsConvertor convertor = vcs.getCustomConvertor();
    final List<VirtualFile> result = convertor != null ? convertor.convertRoots(mappings) : mappings;

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

  /**
   * @return mapped roots and all modules inside: modules might have different settings
   * @see com.intellij.openapi.vcs.VcsRootSettings
   */
  @NotNull
  public List<VirtualFile> getDetailedVcsMappings(@NotNull AbstractVcs vcs) {
    // same as above, but no compression
    List<VirtualFile> roots = new ArrayList<>(myMappings.getMappingsAsFilesUnderVcs(vcs));

    Collection<VirtualFile> modules = DefaultVcsRootPolicy.getInstance(myProject).getDefaultVcsRoots();
    Collection<VirtualFile> modulesUnderVcs = ContainerUtil.filter(modules, file -> {
      if (!file.isDirectory()) return false;
      NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
      return root != null && vcs.equals(root.vcs);
    });

    Collections.sort(roots, FilePathComparator.getInstance());

    List<VirtualFile> modulesToAdd = ApplicationManager.getApplication().runReadAction((Computable<List<VirtualFile>>)() -> {
      final FileIndexFacade facade = ServiceManager.getService(myProject, FileIndexFacade.class);
      return ContainerUtil.filter(modulesUnderVcs,
                                  module -> ContainerUtil.or(roots, root -> facade.isValidAncestor(root, module)));
    });

    return new ArrayList<>(ContainerUtil.union(roots, modulesToAdd));
  }
}
