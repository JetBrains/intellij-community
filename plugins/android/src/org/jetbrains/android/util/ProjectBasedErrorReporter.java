package org.jetbrains.android.util;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class ProjectBasedErrorReporter implements ErrorReporter {
  private final Project myProject;

  public ProjectBasedErrorReporter(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void report(@NotNull String message, @NotNull String title) {
    AndroidUtils.reportError(myProject, message, title);
  }
}
