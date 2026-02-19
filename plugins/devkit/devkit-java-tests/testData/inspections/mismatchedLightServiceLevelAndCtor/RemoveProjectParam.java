import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@<warning descr="Light service with a constructor that takes a parameter of type 'Project' must specify '@Service(Service.Level.PROJECT)'">Service</warning>(Service.Level.APP)
final class MyService {
  private <warning descr="Application-level service requires a no-arg or single parameter constructor with 'kotlinx.coroutines.CoroutineScope' type">MyService<caret></warning>(Project project) {}
}
