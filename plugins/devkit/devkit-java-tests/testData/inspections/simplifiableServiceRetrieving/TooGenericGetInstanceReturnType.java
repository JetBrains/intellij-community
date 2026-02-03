import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

interface MyService {
}

@Service(Service.Level.PROJECT)
final class MyServiceImpl implements MyService {
  public static MyService getInstance(Project project) {
    return project.getService(MyServiceImpl.class);
  }

  public static void foo() { }

  public static void foo(Project project) {
    project.getService(MyServiceImpl.class).foo();
  }
}