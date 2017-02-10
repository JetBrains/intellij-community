package org.zmlx.hg4idea.command;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgRemoteCommandExecutor;

import java.util.LinkedList;
import java.util.List;

public class HgIdentifyCommand {

  private final Project project;
  private String source;

  public HgIdentifyCommand(Project project) {
    this.project = project;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  @Nullable
  public HgCommandResult execute(@NotNull ModalityState state) {
    final List<String> arguments = new LinkedList<>();
    arguments.add(source);
    final HgRemoteCommandExecutor executor = new HgRemoteCommandExecutor(project, source, state, false);
    executor.setSilent(true);
    return executor.executeInCurrentThread(null, "identify", arguments);
  }
}
