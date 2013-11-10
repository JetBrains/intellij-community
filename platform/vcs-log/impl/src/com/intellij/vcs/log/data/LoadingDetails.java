package com.intellij.vcs.log.data;

import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.SimpleContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.ContentRevisionFactory;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.VcsFullCommitDetailsImpl;
import com.intellij.vcs.log.impl.VcsUserImpl;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;

/**
 * Fake {@link VcsFullCommitDetailsImpl} implementation that is used to indicate that details are not ready for the moment,
 * they are being retrieved from the VCS.
 *
 * @author Kirill Likhodedov
 */
public class LoadingDetails extends VcsFullCommitDetailsImpl {

  private static final VcsUserImpl STUB_USER = new VcsUserImpl("", "");

  private final long myLoadingTaskIndex;

  public LoadingDetails(@NotNull Hash hash, long loadingTaskIndex, @NotNull VirtualFile root) {
    super(hash, Collections.<Hash>emptyList(), -1, root, "Loading...", STUB_USER, "", STUB_USER, -1,
          Collections.<Change>emptyList(), new ContentRevisionFactory() {
            @NotNull
            @Override
            public ContentRevision createRevision(@NotNull VirtualFile file, @NotNull Hash hash) {
              return new SimpleContentRevision("", new FilePathImpl(file), hash.asString());
            }

            @NotNull
            @Override
            public ContentRevision createRevision(@NotNull VirtualFile root, @NotNull String path, @NotNull Hash hash) {
              return new SimpleContentRevision("", new FilePathImpl(new File(path), false), hash.asString());
            }
          });
    myLoadingTaskIndex = loadingTaskIndex;
  }

  public long getLoadingTaskIndex() {
    return myLoadingTaskIndex;
  }

}
