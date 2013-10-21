package com.intellij.openapi.vcs.diff;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;

import java.util.Map;

/**
 * @author peter
 */
public abstract class DiffProviderEx implements DiffProvider {
  public Map<VirtualFile, VcsRevisionNumber> getCurrentRevisions(Iterable<VirtualFile> files) {
    return getCurrentRevisions(files, this);
  }

  public static Map<VirtualFile, VcsRevisionNumber> getCurrentRevisions(Iterable<VirtualFile> file, DiffProvider provider) {
    Map<VirtualFile, VcsRevisionNumber> result = ContainerUtil.newHashMap();
    for (VirtualFile virtualFile : file) {
      result.put(virtualFile, provider.getCurrentRevision(virtualFile));
    }
    return result;
  }
}
