package org.jetbrains.android.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
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
    Messages.showErrorDialog(myProject, message, title);
  }
}
