package org.zmlx.hg4idea.command;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResultHandler;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Andrey Kolomoets
 */
public class HgBranchCloseCommand {

  private final Project project;
  private final VirtualFile repo;
  private final String commitMessage;

  public HgBranchCloseCommand(@NotNull Project project, @NotNull VirtualFile repo, @NotNull String commitMessage) {
    this.project = project;
    this.repo = repo;
    this.commitMessage = commitMessage;
  }

  public void execute(@Nullable HgCommandResultHandler resultHandler) throws HgCommandException {
    if (StringUtil.isEmptyOrSpaces(commitMessage)) {
      throw new HgCommandException("commit message is empty");
    }
    List<String> params = new LinkedList<String>();
    params.add("--close-branch");
    params.add("-m");
    params.add(commitMessage);
    new HgCommandExecutor(project).execute(repo, "commit", params, resultHandler);
  }
}
