// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.Nls;

import java.util.Comparator;

/**
 * @author yole
 */
public interface ChangeListGroupingStrategy {
  void beforeStart();
  boolean changedSinceApply();
  @Nls
  String getGroupName(CommittedChangeList changeList);
  Comparator<CommittedChangeList> getComparator();
  @Nls
  String toString();

  ChangeListGroupingStrategy USER = new ChangeListGroupingStrategy() {
    public String toString() {
      return VcsBundle.message("user.group.title");
    }

    @Override
    public String getGroupName(final CommittedChangeList changeList) {
      return changeList.getCommitterName();
    }

    @Override
    public void beforeStart() {
    }

    @Override
    public boolean changedSinceApply() {
      return false;
    }

    @Override
    public Comparator<CommittedChangeList> getComparator() {
      return (o1, o2) -> {
        int rc = o1.getCommitterName().compareToIgnoreCase(o2.getCommitterName());
        if (rc == 0) {
          return -o1.getCommitDate().compareTo(o2.getCommitDate());
        }
        return rc;
      };
    }
  };
}
