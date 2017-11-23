package org.zmlx.hg4idea.command;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
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

  public HgCommandResult execute(@NotNull VirtualFile repositoryRoot) {
    final HgCommandExecutor executor = new HgCommandExecutor(myProject, repositoryRoot.getPath());
    executor.setShowOutput(true);
    return executor.executeInCurrentThread(null, "init", Collections.singletonList(repositoryRoot.getPath()));
  }
}
