// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package git4idea;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Given VFS content roots, filters them and returns only those, which are actual Git roots.
 */
public class GitRootConverter implements AbstractVcs.RootsConvertor {

  public static final GitRootConverter INSTANCE = new GitRootConverter();

  @Override
  @NotNull
  public List<VirtualFile> convertRoots(@NotNull List<VirtualFile> result) {
    // TODO this should be faster, because it is called rather often. gitRootOrNull could be a bottle-neck.
    ArrayList<VirtualFile> roots = new ArrayList<>();
    HashSet<VirtualFile> listed = new HashSet<>();
    for (VirtualFile f : result) {
      VirtualFile r = GitUtil.gitRootOrNull(f);
      if (r != null && listed.add(r)) {
        roots.add(r);
      }
    }
    return roots;
  }
}
