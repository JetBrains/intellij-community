package org.zmlx.hg4idea.command;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class HgIdentifyCommand {

  private final Project project;
  private final HgCommandAuthenticator authenticator = new HgCommandAuthenticator();
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
  public HgCommandResult execute() {
    final List<String> arguments = new LinkedList<String>();
    arguments.add(source);
    return authenticator.executeCommandAndAuthenticateIfNecessary(project, null, source, "identify", arguments);
  }
}
