package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collections;
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
        if (ExcludedFileIndex.getInstance(myProject).isValidAncestor(result.get(i-1), result.get(i))) {
          result.remove(i);
        }
        else {
          i++;
        }
      }
    }
    return result.toArray(new VirtualFile[result.size()]);
  }
}
