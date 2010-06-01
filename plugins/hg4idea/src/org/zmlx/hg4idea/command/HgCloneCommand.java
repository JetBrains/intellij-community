package org.zmlx.hg4idea.command;

import com.intellij.openapi.project.*;

import java.nio.charset.*;
import java.util.*;

public class HgCloneCommand {
  private final Project project;

  private String repositoryURL;
  private String directory;

  public HgCloneCommand(Project project) {
    this.project = project;
  }

  public String getDirectory() {
    return directory;
  }

  public void setDirectory(String directory) {
    this.directory = directory;
  }

  public String getRepositoryURL() {
    return repositoryURL;
  }

  public void setRepositoryURL(String repositoryURL) {
    this.repositoryURL = repositoryURL;
  }

  public HgCommandResult execute() {
    List<String> arguments = new LinkedList<String>();
    arguments.add(repositoryURL);
    arguments.add(directory);

    return HgCommandService.getInstance(project).execute(null, Collections.<String>emptyList(), "clone", arguments, Charset.defaultCharset());
  }
}
