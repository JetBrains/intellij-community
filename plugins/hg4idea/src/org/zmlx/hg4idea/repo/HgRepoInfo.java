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

/**
 * @author Nadya Zabrodina
 */
public class HgRepoInfo {
  @NotNull private String myCurrentBranch = HgRepository.DEFAULT_BRANCH;
  @Nullable private final String myCurrentRevision;
  @NotNull private final Repository.State myState;
  @Nullable private String myCurrentBookmark = null;
  @NotNull private Map<String, Set<Hash>> myBranches = Collections.emptyMap();
  @NotNull private Set<HgNameWithHashInfo> myBookmarks = Collections.emptySet();
  @NotNull private Set<HgNameWithHashInfo> myTags = Collections.emptySet();
  @NotNull private Set<HgNameWithHashInfo> myLocalTags = Collections.emptySet();

  public HgRepoInfo(@NotNull String currentBranch,
                    @Nullable String currentRevision,
                    @NotNull Repository.State state,
                    @NotNull Map<String,Set<Hash>> branches,
                    @NotNull Collection<HgNameWithHashInfo> bookmarks,
                    @Nullable String currentBookmark,
                    @NotNull Collection<HgNameWithHashInfo> tags,
                    @NotNull Collection<HgNameWithHashInfo> localTags) {
    myCurrentBranch = currentBranch;
    myCurrentRevision = currentRevision;
    myState = state;
    myBranches = branches;
    myBookmarks = new LinkedHashSet<HgNameWithHashInfo>(bookmarks);
    myCurrentBookmark = currentBookmark;
    myTags = new LinkedHashSet<HgNameWithHashInfo>(tags);
    myLocalTags = new LinkedHashSet<HgNameWithHashInfo>(localTags);
  }

  @NotNull
  public String getCurrentBranch() {
    return myCurrentBranch;
  }

  @NotNull
  public Map<String, Set<Hash>> getBranches() {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HgRepoInfo info = (HgRepoInfo)o;

    if (myState != info.myState) return false;
    if (myCurrentRevision != null ? !myCurrentRevision.equals(info.myCurrentRevision) : info.myCurrentRevision != null) return false;
    if (!myCurrentBranch.equals(info.myCurrentBranch)) return false;
    if (myCurrentBookmark != null ? !myCurrentBookmark.equals(info.myCurrentBookmark) : info.myCurrentBookmark != null) return false;
    if (!myBranches.equals(info.myBranches)) return false;
    if (!myBookmarks.equals(info.myBookmarks)) return false;
    if (!myTags.equals(info.myTags)) return false;
    if (!myLocalTags.equals(info.myLocalTags)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myCurrentBranch, myCurrentRevision, myCurrentBookmark, myState, myBranches, myBookmarks, myTags, myLocalTags);
  }

  @Override
  @NotNull
  public String toString() {
    return String.format("HgRepository{myCurrentBranch=%s, myCurrentRevision='%s', myState=%s}",
                         myCurrentBranch, myCurrentRevision, myState);
  }
}
