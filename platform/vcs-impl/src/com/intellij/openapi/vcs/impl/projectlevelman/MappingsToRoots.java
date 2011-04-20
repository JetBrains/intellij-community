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

import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class MappingsToRoots {
  private final NewMappings myMappings;
  private final Project myProject;

  public MappingsToRoots(final NewMappings mappings, final Project project) {
    myMappings = mappings;
    myProject = project;
  }

  public VirtualFile[] getRootsUnderVcs(final AbstractVcs vcs) {
    List<VirtualFile> result = myMappings.getMappingsAsFilesUnderVcs(vcs);

    final AbstractVcs.RootsConvertor convertor = vcs.getCustomConvertor();
    if (convertor != null) {
      result = convertor.convertRoots(result);
    }

    Collections.sort(result, FilePathComparator.getInstance());
    if (! vcs.allowsNestedRoots()) {
      int i=1;
      while(i < result.size()) {
        final VirtualFile previous = result.get(i - 1);
        final VirtualFile current = result.get(i);
        if (ExcludedFileIndex.getInstance(myProject).isValidAncestor(previous, current)) {
//        if (ExcludedFileIndex.getInstance(myProject).isValidAncestor(previous, current) && vcs.isVersionedDirectory(previous)) {
          result.remove(i);
        }
        else {
          i++;
        }
      }
    }
    return VfsUtil.toVirtualFileArray(result);
  }

  // not only set mappings, but include all modules inside: modules might have different settings
  public List<VirtualFile> getDetailedVcsMappings(final AbstractVcs vcs) {
    // same as above, but no compression
    List<VirtualFile> result = myMappings.getMappingsAsFilesUnderVcs(vcs);

    final AbstractVcs.RootsConvertor convertor = vcs.getCustomConvertor();
    if (convertor != null) {
      result = convertor.convertRoots(result);
    }

    boolean addInnerModules = true;
    final String vcsName = vcs.getName();
    final List<VcsDirectoryMapping> directoryMappings = myMappings.getDirectoryMappings(vcsName);
    for (VcsDirectoryMapping directoryMapping : directoryMappings) {
      if (directoryMapping.isDefaultMapping()) {
        addInnerModules = false;
        break;
      }
    }

    Collections.sort(result, FilePathComparator.getInstance());
    if (addInnerModules) {
      final DefaultVcsRootPolicy defaultVcsRootPolicy = DefaultVcsRootPolicy.getInstance(myProject);
      final List<VirtualFile> modules = new ArrayList<VirtualFile>();
      defaultVcsRootPolicy.addDefaultVcsRoots(myMappings, vcsName, modules);
      final Iterator<VirtualFile> iterator = modules.iterator();
      while (iterator.hasNext()) {
        final VirtualFile module = iterator.next();
        boolean included = false;
        for (VirtualFile root : result) {
          if (ExcludedFileIndex.getInstance(myProject).isValidAncestor(root, module)) {
            included = true;
            break;
          }
        }
        if (! included) {
          iterator.remove();
        }
      }
      result.addAll(modules);
    }

    return result;
  }
}
