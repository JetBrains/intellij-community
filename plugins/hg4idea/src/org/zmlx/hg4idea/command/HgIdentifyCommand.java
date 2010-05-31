package org.zmlx.hg4idea.command;

import com.intellij.openapi.project.*;

import java.nio.charset.*;
import java.util.*;

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

  public HgCommandResult execute() {
    List<String> arguments = new LinkedList<String>();
    arguments.add(source);

    HgCommandService hgCommandService = HgCommandService.getInstance(project);
    return hgCommandService.execute(null, Collections.<String>emptyList(), "identify", arguments, Charset.defaultCharset());
  }
}
