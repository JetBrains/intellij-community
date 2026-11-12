package com.intellij.aggregateworkspace;

public final class WorkspaceRepo {
  private final String name;
  private final String descriptionKey;
  private final String repositoryUrl;

  public WorkspaceRepo(String name, String descriptionKey, String repositoryUrl) {
    this.name = name;
    this.descriptionKey = descriptionKey;
    this.repositoryUrl = repositoryUrl;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return WorkspaceBundle.message(descriptionKey);
  }

  public String getRepositoryUrl() {
    return repositoryUrl;
  }
}
