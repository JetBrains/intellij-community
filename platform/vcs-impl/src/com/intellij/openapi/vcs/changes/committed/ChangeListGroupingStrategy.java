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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NonNls;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * @author yole
 */
public interface ChangeListGroupingStrategy {
  void beforeStart();
  boolean changedSinceApply();
  String getGroupName(CommittedChangeList changeList);
  Comparator<CommittedChangeList> getComparator();

  ;

  ChangeListGroupingStrategy USER = new ChangeListGroupingStrategy() {
    public String toString() {
      return VcsBundle.message("user.group.title");
    }

    public String getGroupName(final CommittedChangeList changeList) {
      return changeList.getCommitterName();
    }

    public void beforeStart() {
    }

    public boolean changedSinceApply() {
      return false;
    }

    public Comparator<CommittedChangeList> getComparator() {
      return new Comparator<CommittedChangeList>() {
        public int compare(final CommittedChangeList o1, final CommittedChangeList o2) {
          int rc = o1.getCommitterName().compareToIgnoreCase(o2.getCommitterName());
          if (rc == 0) {
            return -o1.getCommitDate().compareTo(o2.getCommitDate());
          }
          return rc;
        }
      };
    }
  };
}
