/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zmlx.hg4idea.repo;

import com.google.common.base.Objects;
import com.intellij.dvcs.repo.Repository;
import com.intellij.vcs.log.Hash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgNameWithHashInfo;

import java.util.*;

public class HgRepoInfo {
  @NotNull private String myCurrentBranch = HgRepository.DEFAULT_BRANCH;
  @Nullable private final String myTipRevision;
  @Nullable private final String myCurrentRevision;
  @NotNull private final Repository.State myState;
  @Nullable private String myCurrentBookmark = null;
  @NotNull private Map<String, LinkedHashSet<Hash>> myBranches = Collections.emptyMap();
  @NotNull private Set<HgNameWithHashInfo> myBookmarks = Collections.emptySet();
  @NotNull private Set<HgNameWithHashInfo> myTags = Collections.emptySet();
  @NotNull private Set<HgNameWithHashInfo> myLocalTags = Collections.emptySet();
  @NotNull private Set<HgNameWithHashInfo> mySubrepos = Collections.emptySet();
  @NotNull private List<HgNameWithHashInfo> myMQApplied = Collections.emptyList();
  @NotNull private List<String> myMqNames = Collections.emptyList();

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

  @NotNull
  public String getCurrentBranch() {
    return myCurrentBranch;
  }

  @NotNull
  public Map<String, LinkedHashSet<Hash>> getBranches() {
    return myBranches;
  }

  @NotNull
  public Collection<HgNameWithHashInfo> getBookmarks() {
    return myBookmarks;
  }

  @NotNull
  public Collection<HgNameWithHashInfo> getTags() {
    return myTags;
  }

  @NotNull
  public Collection<HgNameWithHashInfo> getLocalTags() {
    return myLocalTags;
  }

  @Nullable
  public String getTipRevision() {
    return myTipRevision;
  }

  @Nullable
  public String getCurrentRevision() {
    return myCurrentRevision;
  }

  @Nullable
  public String getCurrentBookmark() {
    return myCurrentBookmark;
  }

  @NotNull
  public Repository.State getState() {
    return myState;
  }

  @NotNull
  public List<HgNameWithHashInfo> getMQApplied() {
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
    return Objects.hashCode(myCurrentBranch, myCurrentRevision, myTipRevision, myCurrentBookmark, myState, myBranches, myBookmarks, myTags,
                            myLocalTags, mySubrepos, myMQApplied, myMqNames);
  }

  @Override
  @NotNull
  public String toString() {
    return String.format("HgRepository{myCurrentBranch=%s, myCurrentRevision='%s', myState=%s}",
                         myCurrentBranch, myCurrentRevision, myState);
  }

  public boolean hasSubrepos() {
    return !mySubrepos.isEmpty();
  }

  @NotNull
  public Collection<HgNameWithHashInfo> getSubrepos() {
    return mySubrepos;
  }
}
