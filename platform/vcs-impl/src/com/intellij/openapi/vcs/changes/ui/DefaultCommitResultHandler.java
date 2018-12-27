// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.CommitResultHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.*;
import static com.intellij.openapi.vcs.VcsBundle.message;
import static com.intellij.openapi.vcs.changes.ui.CommitHelper.collectErrors;

public class DefaultCommitResultHandler implements CommitResultHandler {
  @NotNull private final CommitHelper myHelper;

  public DefaultCommitResultHandler(@NotNull CommitHelper helper) {
    myHelper = helper;
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
    List<VcsException> allExceptions = myHelper.getExceptions();
    List<VcsException> errors = collectErrors(allExceptions);
    int errorsSize = errors.size();
    int warningsSize = allExceptions.size() - errorsSize;

    VcsNotifier notifier = VcsNotifier.getInstance(myHelper.getProject());
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
    StringBuilder content = new StringBuilder(getFileSummaryReport());
    String commitMessage = myHelper.getCommitMessage();
    if (!isEmpty(commitMessage)) {
      content.append(": ").append(escape(commitMessage));
    }
    Set<String> feedback = myHelper.getFeedback();
    if (!feedback.isEmpty()) {
      content.append("<br/>");
      content.append(join(feedback, "<br/>"));
    }
    List<VcsException> exceptions = myHelper.getExceptions();
    if (!hasOnlyWarnings(exceptions)) {
      content.append("<br/>");
      content.append(join(exceptions, Throwable::getMessage, "<br/>"));
    }
    return content.toString();
  }

  @NotNull
  private String getFileSummaryReport() {
    int failed = myHelper.getFailedToCommitChanges().size();
    int committed = myHelper.getChanges().size() - failed;
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
    List<String> FROM = Arrays.asList("<", ">");
    List<String> TO = Arrays.asList("&lt;", "&gt;");
    return replace(s, FROM, TO);
  }

  private static boolean hasOnlyWarnings(@NotNull List<? extends VcsException> exceptions) {
    return exceptions.stream().allMatch(VcsException::isWarning);
  }
}
