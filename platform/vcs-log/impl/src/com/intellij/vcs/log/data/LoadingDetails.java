package com.intellij.vcs.log.data;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.VcsChangesLazilyParsedDetails;
import com.intellij.vcs.log.impl.VcsUserImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * Fake {@link com.intellij.vcs.log.impl.VcsCommitMetadataImpl} implementation that is used to indicate that details are not ready for the moment,
 * they are being retrieved from the VCS.
 *
 * @author Kirill Likhodedov
 */
public class LoadingDetails extends VcsChangesLazilyParsedDetails {

  private static final VcsUserImpl STUB_USER = new VcsUserImpl("", "");

  private final long myLoadingTaskIndex;

  public LoadingDetails(@NotNull Computable<Hash> computableHash, long loadingTaskIndex, @NotNull VirtualFile root) {
    super(new LazyHash(computableHash), Collections.<Hash>emptyList(), -1, root, "Loading...", STUB_USER, "", STUB_USER, -1,
          new ThrowableComputable<Collection<Change>, Exception>() {
      @Override
      public Collection<Change> compute() throws Exception {
        return Collections.emptyList();
      }
    });
    myLoadingTaskIndex = loadingTaskIndex;
  }


  public long getLoadingTaskIndex() {
    return myLoadingTaskIndex;
  }

  private static class LazyHash implements Hash {
    @NotNull
    private final Computable<Hash> myComputableHash;
    @Nullable
    private volatile Hash myHash;

    public LazyHash(@NotNull Computable<Hash> computableHash) {
      myComputableHash = computableHash;
    }

    @NotNull
    private Hash getValue() {
      if (myHash == null) {
        myHash = myComputableHash.compute();
      }
      return myHash;
    }

    @NotNull
    @Override
    public String asString() {
      return getValue().asString();
    }

    @NotNull
    @Override
    public String toShortString() {
      return getValue().toShortString();
    }
  }
}
