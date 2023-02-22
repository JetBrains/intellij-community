import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import kotlinx.coroutines.CoroutineScope;

@Service(Service.Level.APP)
final class MyService {
  private MyService(CoroutineScope scope) {}
}
