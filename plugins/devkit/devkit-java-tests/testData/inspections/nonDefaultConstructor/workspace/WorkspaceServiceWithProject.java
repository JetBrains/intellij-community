package workspace;

public class WorkspaceServiceWithProject {
  public <error descr="Service should not have constructor with parameters (except Workspace)">WorkspaceServiceWithProject</error>(com.intellij.openapi.project.Project project) {}
}
