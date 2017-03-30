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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitResultHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.*;
import static com.intellij.openapi.vcs.VcsBundle.message;
import static com.intellij.openapi.vcs.changes.ui.CommitHelper.collectErrors;
import static com.intellij.openapi.vcs.changes.ui.CommitHelper.hasOnlyWarnings;

public class DefaultCommitResultHandler implements CommitResultHandler {

  @NotNull private final Project myProject;
  @NotNull private final Collection<Change> myIncludedChanges;
  @NotNull private final String myCommitMessage;
  @NotNull private final CommitHelper.GeneralCommitProcessor myCommitProcessor;
  @NotNull private final Set<String> myFeedback;

  public DefaultCommitResultHandler(@NotNull Project project,
                                    @NotNull Collection<Change> includedChanges,
                                    @NotNull String commitMessage,
                                    @NotNull CommitHelper.GeneralCommitProcessor commitProcessor,
                                    @NotNull Set<String> feedback) {
    myProject = project;
    myIncludedChanges = includedChanges;
    myCommitMessage = commitMessage;
    myCommitProcessor = commitProcessor;
    myFeedback = feedback;
  }

  @Override
  public void onSuccess(@NotNull String commitMessage) {
    reportResult();
  }

  @Override
  public void onFailure() {
    reportResult();
  }

  private void reportResult() {
    List<VcsException> errors = collectErrors(myCommitProcessor.getVcsExceptions());
    int errorsSize = errors.size();
    int warningsSize = myCommitProcessor.getVcsExceptions().size() - errorsSize;

    VcsNotifier notifier = VcsNotifier.getInstance(myProject);
    String message = getCommitSummary();
    if (errorsSize > 0) {
      String title = pluralize(message("message.text.commit.failed.with.error"), errorsSize);
      notifier.notifyError(title, message);
    }
    else if (warningsSize > 0) {
      String title = pluralize(message("message.text.commit.finished.with.warning"), warningsSize);
      notifier.notifyImportantWarning(title, message);
    }
    else {
      notifier.notifySuccess(message);
    }
  }

  @NotNull
  private String getCommitSummary() {
    StringBuilder content = new StringBuilder(getFileSummaryReport(myCommitProcessor.getChangesFailedToCommit()));
    if (!isEmpty(myCommitMessage)) {
      content.append(": ").append(escape(myCommitMessage));
    }
    if (!myFeedback.isEmpty()) {
      content.append("<br/>");
      content.append(join(myFeedback, "<br/>"));
    }
    List<VcsException> exceptions = myCommitProcessor.getVcsExceptions();
    if (!hasOnlyWarnings(exceptions)) {
      content.append("<br/>");
      content.append(join(exceptions, Throwable::getMessage, "<br/>"));
    }
    return content.toString();
  }

  @NotNull
  private String getFileSummaryReport(@NotNull List<Change> changesFailedToCommit) {
    int failed = changesFailedToCommit.size();
    int committed = myIncludedChanges.size() - failed;
    String fileSummary = committed + " " + pluralize("file", committed) + " committed";
    if (failed > 0) {
      fileSummary += ", " + failed + " " + pluralize("file", failed) + " failed to commit";
    }
    return fileSummary;
  }

  /*
    Commit message is passed to NotificationManagerImpl#doNotify and displayed as HTML.
    Thus HTML tag braces (< and >) should be escaped,
    but only they since the text is passed directly to HTML <BODY> tag and is not a part of an attribute or else.
   */
  private static String escape(String s) {
    String[] FROM = {"<", ">"};
    String[] TO = {"&lt;", "&gt;"};
    return replace(s, FROM, TO);
  }
}
