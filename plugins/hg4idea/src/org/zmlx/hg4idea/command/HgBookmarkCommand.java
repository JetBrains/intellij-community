package org.zmlx.hg4idea.command;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgCommandResultHandler;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.util.List;

import static org.zmlx.hg4idea.util.HgUtil.getRepositoryManager;

public class HgBookmarkCommand {
  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myRepo;
  @Nullable private final String myBookmarkName;
  @NotNull private final HgCommandResultHandler myBookmarkResultHandler;

  public HgBookmarkCommand(@NotNull Project project,
                           @NotNull VirtualFile repo,
                           @Nullable String bookmarkName) {
    myProject = project;
    myRepo = repo;
    myBookmarkName = bookmarkName;
    myBookmarkResultHandler = new HgCommandResultHandler() {
      @Override
      public void process(@Nullable HgCommandResult result) {
        if(myProject.isDisposed()) return;
        getRepositoryManager(myProject).updateRepository(myRepo);
        if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
          new HgCommandResultNotifier(myProject)
            .notifyError(result, "Hg Error", "Hg bookmark command failed for " + myBookmarkName);
        }
      }
    };
  }

  public void createBookmark(boolean isActive) throws HgCommandException {
    if (isActive) {
      executeBookmarkCommand();
    }
    else {
      executeBookmarkCommand("--inactive");
    }
  }

  public static void createBookmark(@NotNull List<HgRepository> repositories, @NotNull String name, boolean isActive) {
    for (HgRepository repository : repositories) {
      Project project = repository.getProject();
      try {
        new HgBookmarkCommand(project, repository.getRoot(), name).createBookmark(isActive);
      }
      catch (HgCommandException exception) {
        HgErrorUtil.handleException(project, exception);
      }
    }
  }

  public void deleteBookmark() throws HgCommandException {
    executeBookmarkCommand("-d");  //delete
  }

  private void executeBookmarkCommand(@NotNull String... args) throws HgCommandException {
    if (StringUtil.isEmptyOrSpaces(myBookmarkName)) {
      throw new HgCommandException("bookmark name is empty");
    }
    List<String> arguments = ContainerUtil.newArrayList(args);
    arguments.add(myBookmarkName);
    new HgCommandExecutor(myProject).execute(myRepo, "bookmark", arguments, myBookmarkResultHandler);
  }
}
