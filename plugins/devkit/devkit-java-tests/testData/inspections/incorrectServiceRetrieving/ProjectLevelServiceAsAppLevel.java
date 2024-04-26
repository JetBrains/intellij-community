import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

@Service(Service.Level.PROJECT)
final class MyService {
  void foo() {
    MyService service1 = ApplicationManager.getApplication().<error descr="The project-level service is retrieved as an application-level service">getService</error>(MyService.class);
    MyService service2 = ApplicationManager.getApplication().<error descr="The project-level service is retrieved as an application-level service">getServiceIfCreated</error>(MyService.class);
  }
}
