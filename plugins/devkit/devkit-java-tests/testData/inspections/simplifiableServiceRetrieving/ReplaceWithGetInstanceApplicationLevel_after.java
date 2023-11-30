import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

@Service
final class MyService {
  public static MyService getInstance() {
    return ApplicationManager.getApplication().getService(MyService.class);
  }

  void foo() {
    MyService service = MyService.getInstance();
  }
}