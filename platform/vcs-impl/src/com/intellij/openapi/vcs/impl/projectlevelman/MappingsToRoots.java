// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public final class MappingsToRoots {
  public static VirtualFile @NotNull [] getRootsUnderVcs(@NotNull Project project,
                                                         @NotNull NewMappings newMappings,
                                                         @NotNull AbstractVcs vcs) {
    List<VirtualFile> mappings = newMappings.getMappingsAsFilesUnderVcs(vcs);

    final AbstractVcs.RootsConvertor convertor = vcs.getCustomConvertor();
    final List<VirtualFile> result = convertor != null ? convertor.convertRoots(new ArrayList<>(mappings)) : mappings;

    return filterAllowedRoots(project, result, vcs);
  }

  public static VirtualFile @NotNull [] filterAllowedRoots(@NotNull Project project,
                                                           @NotNull List<VirtualFile> mappings,
                                                           @NotNull AbstractVcs vcs) {
    if (vcs.allowsNestedRoots()) {
      return VfsUtilCore.toVirtualFileArray(mappings);
    }

    List<VirtualFile> result = new ArrayList<>(mappings);
    result.sort(FilePathComparator.getInstance());

    ApplicationManager.getApplication().runReadAction(() -> {
      final FileIndexFacade facade = FileIndexFacade.getInstance(project);
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
    return VfsUtilCore.toVirtualFileArray(result);
  }

  /**
   * @return mapped roots and all modules inside: modules might have different settings
   * @see com.intellij.openapi.vcs.VcsRootSettings
   * @deprecated To be removed
   */
  @NotNull
  @Deprecated(forRemoval = true)
  public static List<VirtualFile> getDetailedVcsMappings(@NotNull Project project,
                                                         @NotNull NewMappings newMappings,
                                                         @NotNull AbstractVcs vcs) {
    // same as above, but no compression
    List<VirtualFile> roots = new ArrayList<>(newMappings.getMappingsAsFilesUnderVcs(vcs));

    Collection<VirtualFile> modules = DefaultVcsRootPolicy.getInstance(project).getDefaultVcsRoots();
    Collection<VirtualFile> modulesUnderVcs = ContainerUtil.filter(modules, file -> {
      if (!file.isDirectory()) return false;
      NewMappings.MappedRoot root = newMappings.getMappedRootFor(file);
      return root != null && vcs.equals(root.vcs);
    });

    List<VirtualFile> modulesToAdd = ApplicationManager.getApplication().runReadAction((Computable<List<VirtualFile>>)() -> {
      final FileIndexFacade facade = FileIndexFacade.getInstance(project);
      return ContainerUtil.filter(modulesUnderVcs,
                                  module -> ContainerUtil.or(roots, root -> facade.isValidAncestor(root, module)));
    });

    return new ArrayList<>(ContainerUtil.union(roots, modulesToAdd));
  }
}
