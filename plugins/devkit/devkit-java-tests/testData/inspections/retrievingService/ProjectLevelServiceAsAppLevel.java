import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

@Service(Service.Level.PROJECT)
final class MyService {
  void foo() {
    MyService service = <error descr="The project-level service is retrieved as an application-level service">ApplicationManager.getApplication().getService(MyService.class)</error>;
  }
}
