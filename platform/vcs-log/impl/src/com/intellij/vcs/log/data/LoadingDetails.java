package com.intellij.vcs.log.data;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsCommitDetails;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * Fake {@link VcsCommitDetails} implementation that is used to indicate that details are not ready for the moment,
 * they are being retrieved from the VCS.
 *
 * @author Kirill Likhodedov
 */
public class LoadingDetails extends VcsCommitDetails {

  public LoadingDetails(@NotNull Hash hash) {
    super(hash, Collections.<Hash>emptyList(), -1, "Loading...", "", "", "", "", "", -1, Collections.<Change>emptyList());
  }

}
