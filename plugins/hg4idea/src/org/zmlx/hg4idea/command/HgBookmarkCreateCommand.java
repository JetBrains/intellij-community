package org.zmlx.hg4idea.command;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResultHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Nadya Zabrodina
 */
public class HgBookmarkCreateCommand {
  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myRepo;
  @Nullable private final String myBookmarkName;
  private final boolean isActive;

  public HgBookmarkCreateCommand(@NotNull Project project,
                                 @NotNull VirtualFile repo,
                                 @Nullable String bookmarkName,
                                 boolean active) {
    myProject = project;
    myRepo = repo;
    myBookmarkName = bookmarkName;
    isActive = active;
  }

  public void execute(@Nullable HgCommandResultHandler resultHandler) throws HgCommandException {
    if (StringUtil.isEmptyOrSpaces(myBookmarkName)) {
      throw new HgCommandException("bookmark name is empty");
    }
    List<String> arguments = new ArrayList<String>();
    arguments.add(myBookmarkName);
    if (!isActive) {
      arguments.add("--inactive");
    }
    new HgCommandExecutor(myProject).execute(myRepo, "bookmark", arguments, resultHandler);
  }
}
