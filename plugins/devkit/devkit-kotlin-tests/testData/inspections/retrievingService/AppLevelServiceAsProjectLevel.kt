import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service(Service.Level.APP)
class MyService {
  fun foo(project: Project) {
    project.<error descr="[NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER] Not enough information to infer type variable T">getService</error>(MyService::class.<error descr="[UNRESOLVED_REFERENCE] Unresolved reference: java">java</error>)
  }
}
