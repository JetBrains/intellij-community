// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config;

import com.intellij.execution.ExecutableValidator;
import com.intellij.openapi.project.Project;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

/**
 * Project service that is used to check whether currently set git executable is valid (just calls 'git version' and parses the output),
 * and to display notification to the user proposing to fix the project set up.
 *
 * @author Kirill Likhodedov
 * @deprecated in favor of {@link GitExecutableManager#identifyVersion(String)} and {@link GitExecutableProblemsNotifier}
 */
@Deprecated(forRemoval = true)
public class GitExecutableValidator extends ExecutableValidator {
  public GitExecutableValidator(@NotNull Project project) {
    super(project,
          GitBundle.message("git.executable.notification.title"),
          GitBundle.message("git.executable.notification.description"),
          GitBundle.message("git.executable.notification.cant.run.in.safe.mode"));
  }

  @Override
  protected String getCurrentExecutable() {
    GitExecutable executable = GitExecutableManager.getInstance().getExecutable(myProject);
    if (executable instanceof GitExecutable.Local) return executable.getExePath();
    return "";
  }

  @Override
  protected @NotNull String getConfigurableDisplayName() {
    return GitBundle.message("settings.git.option.group");
  }

  @Override
  public boolean isExecutableValid(@NotNull String pathToGit) {
    try {
      GitExecutable.Local executable = new GitExecutable.Local(pathToGit);
      GitVersion version = GitExecutableManager.getInstance().identifyVersion(executable);
      return !GitVersion.NULL.equals(version);
    }
    catch (GitVersionIdentificationException e) {
      return false;
    }
  }
}
