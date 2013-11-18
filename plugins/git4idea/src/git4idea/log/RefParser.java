package git4idea.log;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogObjectsFactory;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.impl.HashImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


/**
 * TODO: remove when tags are supported by the {@link git4idea.repo.GitRepositoryReader}.
 *
 * @author erokhins
 */
class RefParser {

  private final VcsLogObjectsFactory myFactory;

  public RefParser(VcsLogObjectsFactory factory) {
    myFactory = factory;
  }

  // e25b7d8f (HEAD, refs/remotes/origin/master, refs/remotes/origin/HEAD, refs/heads/master)
  public List<VcsRef> parseCommitRefs(@NotNull String input, @NotNull VirtualFile root) {
    int firstSpaceIndex = input.indexOf(' ');
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

  @Nullable
  private static String getRefName(@NotNull String longRefPath, @NotNull String startPatch) {
    String tagPrefix = "tag: ";
    if (longRefPath.startsWith(tagPrefix)) {
      longRefPath = longRefPath.substring(tagPrefix.length());
    }
    if (longRefPath.startsWith(startPatch)) {
      return longRefPath.substring(startPatch.length());
    }
    else {
      return null;
    }
  }

  // example input: fb29c80 refs/tags/92.29
  @Nullable
  private VcsRef createRef(@NotNull Hash hash, @NotNull String longRefPath, @NotNull VirtualFile root) {
    String name = getRefName(longRefPath, "refs/tags/");
    if (name != null) {
      return myFactory.createRef(hash, name, GitRefManager.TAG, root);
    }

    return null;
  }
}
