// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs;

import com.intellij.openapi.util.NlsContexts;
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
  public abstract @NlsContexts.ColumnName String getTitle();

  public abstract Object getValue(T changeList);

  public @Nullable Comparator<T> getComparator() {
    return null;
  }

  // TODO: CompositeCommittedChangesProvider.getColumns() needs to be updated if new standard columns are added 

  public static final ChangeListColumn<CommittedChangeList> DATE = new ChangeListColumn<>() {
    @Override
    public @NotNull String getTitle() {
      return message("column.name.revision.list.date");
    }

    @Override
    public @NotNull Object getValue(@NotNull CommittedChangeList changeList) {
      return formatPrettyDateTime(changeList.getCommitDate());
    }

    @Override
    public @NotNull Comparator<CommittedChangeList> getComparator() {
      // TODO: CommittedChangeListByDateComparator could be utilized here. But currently it is placed in intellij.platform.vcs.impl.
      // TODO: Think of either moving these ChangeListColumn instances to intellij.platform.vcs.impl or move comparator to intellij.platform.vcs.
      return comparing(CommittedChangeList::getCommitDate);
    }
  };

  public static final ChangeListColumn<CommittedChangeList> NAME = new ChangeListColumn<>() {
    @Override
    public @NotNull String getTitle() {
      return message("column.name.revision.list.committer");
    }

    @Override
    public Object getValue(@NotNull CommittedChangeList changeList) {
      return changeList.getCommitterName();
    }

    @Override
    public @NotNull Comparator<CommittedChangeList> getComparator() {
      return (changeList1, changeList2) -> compare(changeList1.getCommitterName(), changeList2.getCommitterName());
    }
  };

  public static final ChangeListColumn<CommittedChangeList> NUMBER =
    new ChangeListNumberColumn(message("column.name.revision.list.number"));

  public static final ChangeListColumn<CommittedChangeList> DESCRIPTION = new ChangeListColumn<>() {
    @Override
    public @NotNull String getTitle() {
      return message("column.name.revision.list.description");
    }

    @Override
    public @NotNull Object getValue(@NotNull CommittedChangeList changeList) {
      return changeList.getName();
    }

    @Override
    public @NotNull Comparator<CommittedChangeList> getComparator() {
      return comparing(list -> list.getName(), String::compareToIgnoreCase);
    }
  };

  public static boolean isCustom(@NotNull ChangeListColumn column) {
    return column != DATE && column != DESCRIPTION && column != NAME && !(column instanceof ChangeListNumberColumn);
  }

  public static class ChangeListNumberColumn extends ChangeListColumn<CommittedChangeList> {
    private final @NlsContexts.ColumnName String myTitle;

    public ChangeListNumberColumn(@NlsContexts.ColumnName String title) {
      myTitle = title;
    }

    @Override
    public String getTitle() {
      return myTitle;
    }

    @Override
    public @NotNull Object getValue(@NotNull CommittedChangeList changeList) {
      return changeList.getNumber();
    }

    @Override
    public @NotNull Comparator<CommittedChangeList> getComparator() {
      return comparingLong(CommittedChangeList::getNumber);
    }
  }
}
