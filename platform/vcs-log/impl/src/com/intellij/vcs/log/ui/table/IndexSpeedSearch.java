// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
        IndexDataGetter dataGetter = myIndex.getDataGetter();
        if (newValue != null && dataGetter != null) {
          String oldValue = (String)evt.getOldValue();
          Collection<VcsUser> usersToExamine = myUserRegistry.getUsers();
          if (oldValue != null && myMatchedUsers != null && newValue.contains(oldValue)) {
            if (myMatchedUsers.isEmpty()) return;
            usersToExamine = myMatchedUsers;
          }
          myMatchedUsers = ContainerUtil.filter(usersToExamine,
                                                user -> compare(VcsUserUtil.getShortPresentation(user), newValue));
          myMatchedByUserCommits = dataGetter.filter(Collections.singletonList(new SimpleVcsLogUserFilter(myMatchedUsers)));
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

    SimpleVcsLogUserFilter(@NotNull Collection<VcsUser> matchedUsers) {
      myMatchedUsers = matchedUsers;
    }

    @NotNull
    @Override
    public Collection<VcsUser> getUsers(@NotNull VirtualFile root) {
      return myMatchedUsers;
    }

    @NotNull
    @Override
    public Collection<String> getValuesAsText() {
      return ContainerUtil.map(myMatchedUsers, user -> VcsUserUtil.toExactString(user));
    }

    @Override
    public boolean matches(@NotNull VcsCommitMetadata details) {
      return myMatchedUsers.contains(details.getAuthor());
    }
  }
}
