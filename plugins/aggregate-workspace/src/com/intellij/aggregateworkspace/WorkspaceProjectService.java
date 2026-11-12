package com.intellij.aggregateworkspace;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class WorkspaceProjectService {
  private static final List<WorkspaceRepo> REPOSITORIES = List.of(
    new WorkspaceRepo("Jupyterlab", "repo.jupyterlab", "https://github.com/joeyr64/Jupyterlab"),
    new WorkspaceRepo("notebook", "repo.notebook", "https://github.com/joeyr64/notebook"),
    new WorkspaceRepo("HUNT", "repo.hunt", "https://github.com/joeyr64/HUNT"),
    new WorkspaceRepo("burpsuite", "repo.burpsuite", "https://github.com/joeyr64/burpsuite"),
    new WorkspaceRepo("BurpKit", "repo.burpkit", "https://github.com/joeyr64/BurpKit")
  );

  private final Project project;

  public WorkspaceProjectService(Project project) {
    this.project = project;
  }

  public List<WorkspaceRepo> getRepositories() {
    return REPOSITORIES;
  }

  @Nullable
  public String getCheckoutPath(WorkspaceRepo repository) {
    return PropertiesComponent.getInstance(project).getValue(repository.getCheckoutPropertyKey());
  }

  public void setCheckoutPath(WorkspaceRepo repository, String path) {
    PropertiesComponent.getInstance(project).setValue(repository.getCheckoutPropertyKey(), path);
  }
}
