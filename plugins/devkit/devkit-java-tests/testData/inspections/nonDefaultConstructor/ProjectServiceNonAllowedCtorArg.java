@com.intellij.openapi.components.Service
public class ProjectServiceNonAllowedCtorArg {
  public <error descr="Service should not have constructor with parameters (except Project or Module if requested on corresponding level)">ProjectServiceNonAllowedCtorArg</error>(com.intellij.openapi.project.Project project, String notAllowed) {}
}
