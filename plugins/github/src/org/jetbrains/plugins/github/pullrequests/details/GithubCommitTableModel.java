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
package org.jetbrains.plugins.github.pullrequests.details;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.pullrequests.details.GithubPullRequestDetailsLoader.GithubCommitDetails;

public class GithubCommitTableModel extends ListTableModel<GithubCommitDetails> {
  public static int VERSION_COL = 0;
  public static int DATE_COL = 1;
  public static int AUTHOR_COL = 2;
  public static int MESSAGE_COL = 3;

  public GithubCommitTableModel() {
    super(VERSION, DATE, AUTHOR, MESSAGE);
  }

  private final static ColumnInfo<GithubCommitDetails, String> VERSION = new ColumnInfo<GithubCommitDetails, String>("Version") {
    @Nullable
    @Override
    public String valueOf(GithubCommitDetails o) {
      return DvcsUtil.getShortHash(o.getCommit().getSha());
    }

    @Nullable
    @Override
    public String getMaxStringValue() {
      return "abcdef01";
    }

    @Nullable
    @Override
    public String getPreferredStringValue() {
      return "abcdef01";
    }
  };

  private final static ColumnInfo<GithubCommitDetails, String> MESSAGE = new ColumnInfo<GithubCommitDetails, String>("Commit Message") {
    @Nullable
    @Override
    public String valueOf(GithubCommitDetails o) {
      return o.getCommit().getCommit().getMessage();
    }

    @Nullable
    @Override
    public String getPreferredStringValue() {
      return "GithubCommitDetails-000000: Some not so long code review title message";
    }
  };

  private final static ColumnInfo<GithubCommitDetails, String> AUTHOR = new ColumnInfo<GithubCommitDetails, String>("Author") {
    @Nullable
    @Override
    public String valueOf(GithubCommitDetails o) {
      return o.getCommit().getCommit().getAuthor().getName();
    }

    @Nullable
    @Override
    public String getPreferredStringValue() {
      return "Casuallong Username";
    }
  };

  private final static ColumnInfo<GithubCommitDetails, String> DATE = new ColumnInfo<GithubCommitDetails, String>("Date") {
    @Nullable
    @Override
    public String valueOf(GithubCommitDetails o) {
      return DateFormatUtil.formatPrettyDateTime(o.getCommit().getCommit().getAuthor().getDate());
    }

    @Nullable
    @Override
    public String getMaxStringValue() {
      return "Yesterday 00:00:00";
    }

    @Nullable
    @Override
    public String getPreferredStringValue() {
      return "Yesterday 00:00:00";
    }
  };
}
