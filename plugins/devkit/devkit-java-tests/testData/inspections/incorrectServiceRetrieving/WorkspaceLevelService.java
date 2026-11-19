import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.workspace.ProjectWorkspace;

@Service(Service.Level.WORKSPACE)
final class MyService {
  void foo(Project project, ProjectWorkspace workspace) {
    MyService service1 = ApplicationManager.getApplication().<error descr="The workspace-level service is retrieved as an application-level service">getService</error>(MyService.class);
    MyService service2 = project.<error descr="The workspace-level service is retrieved as a project-level service">getService</error>(MyService.class);
    MyService service3 = workspace.getService(MyService.class);
    MyService service4 = workspace.getServiceIfCreated(MyService.class);
  }
}
