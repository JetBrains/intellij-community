package git4idea.log;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogObjectsFactory;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import com.intellij.vcs.log.impl.HashImpl;
import git4idea.branch.GitBranchUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class RefParser {

  private final VcsLogObjectsFactory myFactory;

  public RefParser(@NotNull VcsLogObjectsFactory factory) {
    myFactory = factory;
  }

  // e25b7d8f (HEAD, refs/remotes/origin/master, refs/remotes/origin/HEAD, refs/heads/master)
  public List<VcsRef> parseCommitRefs(@NotNull String input, @NotNull VirtualFile root) {
    int firstSpaceIndex = input.indexOf(' ');
    if (firstSpaceIndex < 0) {
      return Collections.emptyList();
    }
    String strHash = input.substring(0, firstSpaceIndex);
    Hash hash = HashImpl.build(strHash);
    String refPaths = input.substring(firstSpaceIndex + 2, input.length() - 1);
    String[] longRefPaths = refPaths.split(", ");
    List<VcsRef> refs = new ArrayList<VcsRef>();
    for (String longRefPatch : longRefPaths) {
      VcsRef ref = createRef(hash, longRefPatch, root);
      if (ref != null) {
        refs.add(ref);
      }
    }
    return refs;
  }

  @NotNull
  private static String getRefName(@NotNull String longRefPath) {
    String tagPrefix = "tag: ";
    if (longRefPath.startsWith(tagPrefix)) {
      longRefPath = longRefPath.substring(tagPrefix.length());
    }
    return longRefPath;
  }

  // example input: fb29c80 refs/tags/92.29
  @Nullable
  private VcsRef createRef(@NotNull Hash hash, @NotNull String longRefPath, @NotNull VirtualFile root) {
    String name = getRefName(longRefPath);
    VcsRefType type = GitRefManager.getRefType(name);
    assert type != null;
    return myFactory.createRef(hash, GitBranchUtil.stripRefsPrefix(name), type, root);
  }

}
