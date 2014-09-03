package org.jetbrains.plugins.github.api;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubChangeIssueStateRequest {
  @NotNull private final String state;

  public GithubChangeIssueStateRequest(@NotNull String state) {
    this.state = state;
  }
}
