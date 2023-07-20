import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service(Service.Level.APP)
final class MyService {
  void foo(Project project) {
    MyService service = project.<error descr="The application-level service is retrieved as a project-level service">getService</error>(MyService.class);
  }
}
