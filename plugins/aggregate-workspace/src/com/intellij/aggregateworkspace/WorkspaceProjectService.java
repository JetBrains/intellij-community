package com.intellij.aggregateworkspace;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import java.util.List;

@Service(Service.Level.PROJECT)
public final class WorkspaceProjectService {
  private final Project project;

  public WorkspaceProjectService(Project project) {
    this.project = project;
  }

  public List<WorkspaceRepo> getRepos() {
    return List.of(
      new WorkspaceRepo("Jupyterlab", "Notebook workspace", true),
      new WorkspaceRepo("notebook", "Jupyter interactive notebook", true),
      new WorkspaceRepo("HUNT", "Python + Kotlin research workspace", true),
      new WorkspaceRepo("burpsuite", "Security tooling suite", true),
      new WorkspaceRepo("BurpKit", "Burp suite plugin tooling", true),
      new WorkspaceRepo("GitHub-Codespaces", "Intentionally excluded", false)
    );
  }

  public Project getProject() {
    return project;
  }
}
