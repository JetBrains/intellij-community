package com.intellij.aggregateworkspace;

import com.intellij.openapi.components.Service;
import java.util.List;

@Service(Service.Level.PROJECT)
public final class WorkspaceProjectService {
  private static final List<WorkspaceRepo> REPOSITORIES = List.of(
    new WorkspaceRepo("Jupyterlab", "repo.jupyterlab", "https://github.com/joeyr64/Jupyterlab"),
    new WorkspaceRepo("notebook", "repo.notebook", "https://github.com/joeyr64/notebook"),
    new WorkspaceRepo("HUNT", "repo.hunt", "https://github.com/joeyr64/HUNT"),
    new WorkspaceRepo("burpsuite", "repo.burpsuite", "https://github.com/joeyr64/burpsuite"),
    new WorkspaceRepo("BurpKit", "repo.burpkit", "https://github.com/joeyr64/BurpKit")
  );

  public List<WorkspaceRepo> getRepositories() {
    return REPOSITORIES;
  }
}
