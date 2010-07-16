package org.zmlx.hg4idea.command;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Representation of the "hg init"
 */
public class HgInitCommand {

  private final Project myProject;

  public HgInitCommand(Project project) {
    myProject = project;
  }

  public boolean execute(@NotNull VirtualFile repositoryRoot) {
    final List<String> args = new ArrayList<String>(1);
    args.add(repositoryRoot.getPath());
    final HgCommandResult result = HgCommandService.getInstance(myProject).execute(null, "init", args);
    return result != null && !HgErrorUtil.isAbort(result);
  }

}
