@com.intellij.openapi.components.Service
public class ProjectServiceNonAllowedCtorArg {
  public <error descr="Service should not have constructor with parameters (except Project or Module if requested on corresponding level).
Do not instantiate services in constructor because they should be requested only when needed.">ProjectServiceNonAllowedCtorArg</error>(com.intellij.openapi.project.Project project, String notAllowed) {}
}
