package org.jetbrains.android.inspections.lint;

import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
class IntellijLintConfiguration extends Configuration {
  private final Set<Issue> myIssueSet;

  IntellijLintConfiguration(@NotNull Collection<Issue> issueSet) {
    myIssueSet = new HashSet<Issue>(issueSet);
  }

  @Override
  public void ignore(Context context, Issue issue, Location location, String message, Object data) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setSeverity(Issue issue, Severity severity) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEnabled(Issue issue) {
    return myIssueSet.contains(issue);
  }

  @Override
  public Severity getSeverity(Issue issue) {
    return Severity.WARNING;
  }
}
