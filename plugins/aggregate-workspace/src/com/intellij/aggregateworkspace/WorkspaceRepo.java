package com.intellij.aggregateworkspace;

public final class WorkspaceRepo {
  private final String name;
  private final String description;
  private final boolean included;

  public WorkspaceRepo(String name, String description, boolean included) {
    this.name = name;
    this.description = description;
    this.included = included;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public boolean isIncluded() {
    return included;
  }
}
