import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@<warning descr="If constructor takes Project, Service.Level.PROJECT is required">Service</warning>(Service.Level.APP)
final class MyService {
  private <warning descr="Application level service requires no-arg constructor or constructor taking Coroutine">MyService<caret></warning>(Project project) {}
}
