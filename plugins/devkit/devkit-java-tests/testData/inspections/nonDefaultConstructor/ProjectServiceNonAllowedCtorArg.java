@com.intellij.openapi.components.Service
public class ProjectServiceNonAllowedCtorArg {
  <error descr="Service should not have constructor with parameters (except Project or Module if requested on corresponding level). To not instantiate services in constructor.">public ProjectServiceNonAllowedCtorArg(com.intellij.openapi.project.Project project, String notAllowed) {}</error>
}
