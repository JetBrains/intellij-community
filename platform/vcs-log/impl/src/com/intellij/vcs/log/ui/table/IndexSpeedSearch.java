/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.table;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogUserFilter;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.VcsUserRegistry;
import com.intellij.vcs.log.data.index.IndexDataGetter;
import com.intellij.vcs.log.data.index.IndexedDetails;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.util.VcsUserUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class IndexSpeedSearch extends VcsLogSpeedSearch {
  @NotNull private final VcsLogIndex myIndex;
  @NotNull private final VcsUserRegistry myUserRegistry;

  @Nullable private Set<Integer> myMatchedByUserCommits;
  @Nullable private Collection<VcsUser> myMatchedUsers;

  public IndexSpeedSearch(@NotNull Project project, @NotNull VcsLogIndex index, @NotNull VcsLogGraphTable component) {
    super(component);
    myIndex = index;
    myUserRegistry = ServiceManager.getService(project, VcsUserRegistry.class);

    addChangeListener(evt -> {
      if (evt.getPropertyName().equals(ENTERED_PREFIX_PROPERTY_NAME)) {
        String newValue = (String)evt.getNewValue();
        if (newValue != null) {
          String oldValue = (String)evt.getOldValue();
          Collection<VcsUser> usersToExamine = myUserRegistry.getUsers();
          if (oldValue != null && newValue.contains(oldValue) && myMatchedUsers != null) {
            if (myMatchedUsers.isEmpty()) return;
            usersToExamine = myMatchedUsers;
          }
          myMatchedUsers = ContainerUtil.filter(usersToExamine,
                                                user -> compare(VcsUserUtil.getShortPresentation(user), newValue));
          myMatchedByUserCommits = myIndex.filter(Collections.singletonList(new SimpleVcsLogUserFilter(myMatchedUsers)));
        }
        else {
          myMatchedByUserCommits = null;
          myMatchedUsers = null;
        }
      }
    });
  }

  @Override
  protected boolean isSpeedSearchEnabled() {
    if (super.isSpeedSearchEnabled()) {
      VisiblePack visiblePack = myComponent.getModel().getVisiblePack();
      Set<VirtualFile> roots = visiblePack.getLogProviders().keySet();
      Set<VirtualFile> visibleRoots = VcsLogUtil.getAllVisibleRoots(roots, visiblePack.getFilters());
      for (VirtualFile root : visibleRoots) {
        if (!myIndex.isIndexed(root)) return false;
      }
      return true;
    }
    return false;
  }

  @Nullable
  @Override
  protected String getElementText(@NotNull Object row) {
    throw new UnsupportedOperationException(
      "Getting row text in a Log is unsupported since we match commit subject and author separately.");
  }

  @Nullable
  private String getCommitSubject(@NotNull Integer row) {
    IndexDataGetter dataGetter = myIndex.getDataGetter();
    if (dataGetter != null) {
      Integer id = myComponent.getModel().getIdAtRow(row);
      String message = dataGetter.getFullMessage(id);
      if (message != null) return IndexedDetails.getSubject(message);
    }
    return super.getElementText(row);
  }

  @Override
  protected boolean isMatchingElement(Object row, String pattern) {
    String str = getCommitSubject((Integer)row);
    return (str != null && compare(str, pattern)) ||
           (myMatchedByUserCommits != null &&
            !myMatchedByUserCommits.isEmpty() &&
            // getting id from row takes time, so optimizing a little here
            myMatchedByUserCommits.contains(myComponent.getModel().getIdAtRow((Integer)row)));
  }

  private static class SimpleVcsLogUserFilter implements VcsLogUserFilter {
    @NotNull private final Collection<VcsUser> myMatchedUsers;

    public SimpleVcsLogUserFilter(@NotNull Collection<VcsUser> matchedUsers) {
      myMatchedUsers = matchedUsers;
    }

    @NotNull
    @Override
    public Collection<VcsUser> getUsers(@NotNull VirtualFile root) {
      return myMatchedUsers;
    }

    @Override
    public boolean matches(@NotNull VcsCommitMetadata details) {
      return myMatchedUsers.contains(details.getAuthor());
    }
  }
}
