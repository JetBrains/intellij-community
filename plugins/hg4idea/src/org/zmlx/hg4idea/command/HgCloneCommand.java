package org.zmlx.hg4idea.command;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class HgCloneCommand {
  private final Project project;

  private String repositoryURL;
  private String directory;

  public HgCloneCommand(Project project) {
    this.project = project;
  }

  public void setDirectory(String directory) {
    this.directory = directory;
  }

  public void setRepositoryURL(String repositoryURL) {
    this.repositoryURL = repositoryURL;
  }

  @Nullable
  public HgCommandResult execute() {
    List<String> arguments = new LinkedList<String>();
    arguments.add(repositoryURL);
    arguments.add(directory);
    return HgCommandService.getInstance(project).execute(null, Collections.<String>emptyList(), "clone", arguments, Charset.defaultCharset());
  }
}
