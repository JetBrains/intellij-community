package com.intellij.aggregateworkspace;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class WorkspaceProjectService {
  private final Project project;

  public WorkspaceProjectService(Project project) {
    this.project = project;
  }

  public List<WorkspaceRepository> getRepositories() {
    return WorkspaceRepositoryRegistry.INSTANCE.getRepositories();
  }

  @Nullable
  public String getCheckoutPath(String repositoryId) {
    for (WorkspaceRepository repo : getRepositories()) {
      if (repo.getId().equals(repositoryId)) {
        return PropertiesComponent.getInstance(project).getValue(repo.getCheckoutPropertyKey());
      }
    }
    return null;
  }

  public void setCheckoutPath(String repositoryId, String path) {
    for (WorkspaceRepository repo : getRepositories()) {
      if (repo.getId().equals(repositoryId)) {
        PropertiesComponent.getInstance(project).setValue(repo.getCheckoutPropertyKey(), path);
        return;
      }
    }
  }

  public Project getProject() {
    return project;
  }
}
