import com.intellij.openapi.components.Service;
import com.intellij.openapi.workspace.ProjectWorkspace;

@Service(Service.Level.APP)
final class MyAppService {
}

@Service(Service.Level.PROJECT)
final class MyProjectService {
  void foo(ProjectWorkspace workspace) {
    MyAppService service1 = workspace.<error descr="The application-level service is retrieved as a workspace-level service">getService</error>(MyAppService.class);
    MyProjectService service2 = workspace.<error descr="The project-level service is retrieved as a workspace-level service">getService</error>(MyProjectService.class);
  }
}
