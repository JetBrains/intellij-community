// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.repo;

import com.intellij.dvcs.repo.Repository;
import com.intellij.vcs.log.Hash;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgNameWithHashInfo;

import java.util.*;

public class HgRepoInfo {
  private final @NotNull String myCurrentBranch;
  private final @Nullable String myTipRevision;
  private final @Nullable String myCurrentRevision;
  private final @NotNull Repository.State myState;
  private final @Nullable String myCurrentBookmark;
  private final @NotNull Map<String, LinkedHashSet<Hash>> myBranches;
  private final @NotNull Set<HgNameWithHashInfo> myBookmarks;
  private final @NotNull Set<HgNameWithHashInfo> myTags;
  private final @NotNull Set<HgNameWithHashInfo> myLocalTags;
  private final @NotNull Set<HgNameWithHashInfo> mySubrepos;
  private final @NotNull List<HgNameWithHashInfo> myMQApplied;
  private final @NotNull List<String> myMqNames;

  public HgRepoInfo(@NotNull String currentBranch,
                    @Nullable String currentRevision,
                    @Nullable String currentTipRevision,
                    @NotNull Repository.State state,
                    @NotNull Map<String, LinkedHashSet<Hash>> branches,
                    @NotNull Collection<HgNameWithHashInfo> bookmarks,
                    @Nullable String currentBookmark,
                    @NotNull Collection<HgNameWithHashInfo> tags,
                    @NotNull Collection<HgNameWithHashInfo> localTags, @NotNull Collection<HgNameWithHashInfo> subrepos,
                    @NotNull List<HgNameWithHashInfo> mqApplied, @NotNull List<String> mqNames) {
    myCurrentBranch = currentBranch;
    myCurrentRevision = currentRevision;
    myTipRevision = currentTipRevision;
    myState = state;
    myBranches = branches;
    myBookmarks = new LinkedHashSet<>(bookmarks);
    myCurrentBookmark = currentBookmark;
    myTags = new LinkedHashSet<>(tags);
    myLocalTags = new LinkedHashSet<>(localTags);
    mySubrepos = new HashSet<>(subrepos);
    myMQApplied = mqApplied;
    myMqNames = mqNames;
  }

  public @NotNull String getCurrentBranch() {
    return myCurrentBranch;
  }

  public @NotNull Map<String, LinkedHashSet<Hash>> getBranches() {
    return myBranches;
  }

  public @NotNull Collection<HgNameWithHashInfo> getBookmarks() {
    return myBookmarks;
  }

  public @NotNull Collection<HgNameWithHashInfo> getTags() {
    return myTags;
  }

  public @NotNull Collection<HgNameWithHashInfo> getLocalTags() {
    return myLocalTags;
  }

  public @Nullable String getTipRevision() {
    return myTipRevision;
  }

  public @Nullable String getCurrentRevision() {
    return myCurrentRevision;
  }

  public @Nullable String getCurrentBookmark() {
    return myCurrentBookmark;
  }

  public @NotNull Repository.State getState() {
    return myState;
  }

  public @NotNull List<HgNameWithHashInfo> getMQApplied() {
    return myMQApplied;
  }

  public List<String> getMqPatchNames() {
    return myMqNames;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HgRepoInfo info = (HgRepoInfo)o;

    if (myState != info.myState) return false;
    if (myTipRevision != null ? !myTipRevision.equals(info.myTipRevision) : info.myTipRevision != null) return false;
    if (myCurrentRevision != null ? !myCurrentRevision.equals(info.myCurrentRevision) : info.myCurrentRevision != null) return false;
    if (!myCurrentBranch.equals(info.myCurrentBranch)) return false;
    if (myCurrentBookmark != null ? !myCurrentBookmark.equals(info.myCurrentBookmark) : info.myCurrentBookmark != null) return false;
    if (!myBranches.equals(info.myBranches)) return false;
    if (!myBookmarks.equals(info.myBookmarks)) return false;
    if (!myTags.equals(info.myTags)) return false;
    if (!myLocalTags.equals(info.myLocalTags)) return false;
    if (!mySubrepos.equals(info.mySubrepos)) return false;
    if (!myMQApplied.equals(info.myMQApplied)) return false;
    if (!myMqNames.equals(info.myMqNames)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myCurrentBranch, myCurrentRevision, myTipRevision, myCurrentBookmark, myState, myBranches, myBookmarks, myTags,
                        myLocalTags, mySubrepos, myMQApplied, myMqNames);
  }

  @Override
  public @NonNls @NotNull String toString() {
    return String.format("HgRepository{myCurrentBranch=%s, myCurrentRevision='%s', myState=%s}",
                         myCurrentBranch, myCurrentRevision, myState);
  }

  public boolean hasSubrepos() {
    return !mySubrepos.isEmpty();
  }

  public @NotNull Collection<HgNameWithHashInfo> getSubrepos() {
    return mySubrepos;
  }
}