package com.intellij.vcs.log.data;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.VcsChangesLazilyParsedDetails;
import com.intellij.vcs.log.impl.VcsUserImpl;
import org.jetbrains.annotations.NotNull;

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

  public LoadingDetails(@NotNull Hash hash, long loadingTaskIndex, @NotNull VirtualFile root) {
    super(hash, Collections.<Hash>emptyList(), -1, root, "Loading...", STUB_USER, "", STUB_USER, -1,
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

}
