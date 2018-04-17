/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.vcs;

import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

import static com.intellij.openapi.util.Comparing.compare;
import static com.intellij.openapi.vcs.VcsBundle.message;
import static com.intellij.util.text.DateFormatUtil.formatPrettyDateTime;
import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingLong;

public abstract class ChangeListColumn<T extends ChangeList> {
  public abstract String getTitle();
  public abstract Object getValue(T changeList);

  @Nullable
  public Comparator<T> getComparator() {
    return null;
  }

  // TODO: CompositeCommittedChangesProvider.getColumns() needs to be updated if new standard columns are added 

  public static final ChangeListColumn<CommittedChangeList> DATE = new ChangeListColumn<CommittedChangeList>() {
    @Override
    @NotNull
    public String getTitle() {
      return message("column.name.revision.list.date");
    }

    @Override
    @NotNull
    public Object getValue(@NotNull CommittedChangeList changeList) {
      return formatPrettyDateTime(changeList.getCommitDate());
    }

    @Override
    @NotNull
    public Comparator<CommittedChangeList> getComparator() {
      // TODO: CommittedChangeListByDateComparator could be utilized here. But currently it is placed in intellij.platform.vcs.impl.
      // TODO: Think of either moving these ChangeListColumn instances to intellij.platform.vcs.impl or move comparator to intellij.platform.vcs.
      return comparing(CommittedChangeList::getCommitDate);
    }
  };

  public static final ChangeListColumn<CommittedChangeList> NAME = new ChangeListColumn<CommittedChangeList>() {
    @Override
    @NotNull
    public String getTitle() {
      return message("column.name.revision.list.committer");
    }

    @Override
    public Object getValue(@NotNull CommittedChangeList changeList) {
      return changeList.getCommitterName();
    }

    @Override
    @NotNull
    public Comparator<CommittedChangeList> getComparator() {
      return (changeList1, changeList2) -> compare(changeList1.getCommitterName(), changeList2.getCommitterName());
    }
  };

  public static final ChangeListColumn<CommittedChangeList> NUMBER =
    new ChangeListNumberColumn(message("column.name.revision.list.number"));

  public static final ChangeListColumn<CommittedChangeList> DESCRIPTION = new ChangeListColumn<CommittedChangeList>() {
    @Override
    @NotNull
    public String getTitle() {
      return message("column.name.revision.list.description");
    }

    @Override
    @NotNull
    public Object getValue(@NotNull CommittedChangeList changeList) {
      return changeList.getName();
    }

    @Override
    @NotNull
    public Comparator<CommittedChangeList> getComparator() {
      return comparing(list -> list.getName(), String::compareToIgnoreCase);
    }
  };

  public static boolean isCustom(@NotNull ChangeListColumn column) {
    return column != DATE && column != DESCRIPTION && column != NAME && !(column instanceof ChangeListNumberColumn);
  }

  public static class ChangeListNumberColumn extends ChangeListColumn<CommittedChangeList> {
    private final String myTitle;

    public ChangeListNumberColumn(String title) {
      myTitle = title;
    }

    @Override
    public String getTitle() {
      return myTitle;
    }

    @Override
    @NotNull
    public Object getValue(@NotNull CommittedChangeList changeList) {
      return changeList.getNumber();
    }

    @Override
    @NotNull
    public Comparator<CommittedChangeList> getComparator() {
      return comparingLong(CommittedChangeList::getNumber);
    }
  }
}
