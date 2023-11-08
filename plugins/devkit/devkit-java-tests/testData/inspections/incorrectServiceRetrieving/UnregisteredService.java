import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

final class MyService {
  void foo(Project project) {
    MyService service = project.<error descr="The 'MyService' class is not registered as a service">getService</error>(MyService.class);
  }
}
