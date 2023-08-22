// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsFreezingProcess;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @see VcsFreezingProcess
 */
public class GitFreezingProcess extends VcsFreezingProcess {
  public GitFreezingProcess(@NotNull Project project, @NotNull @Nls String operationTitle, @NotNull Runnable runnable) {
    super(project, GitBundle.message("local.changes.freeze.message.git.operation.prefix", operationTitle), runnable);
  }
}
