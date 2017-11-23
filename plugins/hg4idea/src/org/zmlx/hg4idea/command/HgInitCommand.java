package org.zmlx.hg4idea.command;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;

import java.util.Collections;

/**
 * Representation of the "hg init"
 */
public class HgInitCommand {

  private final Project myProject;

  public HgInitCommand(@NotNull Project project) {
    myProject = project;
  }

  public HgCommandResult execute(@NotNull String rootPath) {
    final HgCommandExecutor executor = new HgCommandExecutor(myProject, rootPath);
    executor.setShowOutput(true);
    return executor.executeInCurrentThread(null, "init", Collections.singletonList(rootPath));
  }
}
