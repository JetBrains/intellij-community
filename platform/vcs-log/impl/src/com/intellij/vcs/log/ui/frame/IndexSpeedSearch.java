/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogUserFilter;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.VcsUserRegistry;
import com.intellij.vcs.log.data.VisiblePack;
import com.intellij.vcs.log.data.index.IndexedDetails;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.util.VcsUserUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class IndexSpeedSearch extends VcsLogSpeedSearch {
  @NotNull private final VcsLogIndex myIndex;
  @NotNull private final VcsUserRegistry myUserRegistry;

  @Nullable private Set<Integer> myMatchedByUserCommits;

  public IndexSpeedSearch(@NotNull Project project, @NotNull VcsLogIndex index, @NotNull VcsLogGraphTable component) {
    super(component);
    myIndex = index;
    myUserRegistry = ServiceManager.getService(project, VcsUserRegistry.class);

    addChangeListener(evt -> {
      if (evt.getPropertyName().equals(ENTERED_PREFIX_PROPERTY_NAME)) {
        String pattern = (String)evt.getNewValue();
        if (pattern != null) {
          List<VcsUser> matchedUsers = ContainerUtil.filter(myUserRegistry.getUsers(),
                                                            user -> compare(VcsUserUtil.getShortPresentation(user), pattern));
          myMatchedByUserCommits = myIndex.filter(Collections.singletonList(new SimpleVcsLogUserFilter(matchedUsers)));
        }
        else {
          myMatchedByUserCommits = null;
        }
      }
    });
  }

  @Override
  protected boolean isSpeedSearchEnabled() {
    if (super.isSpeedSearchEnabled()) {
      VisiblePack visiblePack = myComponent.getModel().getVisiblePack();
      Set<VirtualFile> roots = visiblePack.getLogProviders().keySet();
      Set<VirtualFile> visibleRoots = VcsLogUtil.getAllVisibleRoots(roots, visiblePack.getFilters().getRootFilter(),
                                                                    visiblePack.getFilters().getStructureFilter());
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
    Integer id = myComponent.getModel().getIdAtRow((Integer)row);
    String message = myIndex.getFullMessage(id);
    if (message == null) return super.getElementText(row);
    return IndexedDetails.getSubject(message);
  }

  @Override
  protected boolean isMatchingElement(Object row, String pattern) {
    String str = this.getElementText(row);
    return (str != null && compare(str, pattern)) ||
           (myMatchedByUserCommits != null &&
            !myMatchedByUserCommits.isEmpty() &&
            // getting id from row takes time, so optimizing a little here
            myMatchedByUserCommits.contains(myComponent.getModel().getIdAtRow((Integer)row)));
  }

  private static class SimpleVcsLogUserFilter implements VcsLogUserFilter {
    @NotNull private final List<VcsUser> myMatchedUsers;

    public SimpleVcsLogUserFilter(@NotNull List<VcsUser> matchedUsers) {
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
