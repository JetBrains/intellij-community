import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

@Service
final class MyService {
  public static MyService getInstance() {
    return ApplicationManager.getApplication().getService(MyService.class);
  }

  void foo() {
    MyService service = <weak_warning descr="Can be replaced with 'MyService.getInstance()' call">ApplicationManager.getApplication().getService<caret>(MyService.class)</weak_warning>;
  }
}