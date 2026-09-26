import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import serviceDeclarations.RegisteredProjectService;
import serviceDeclarations.RegisteredApplicationService;

class MyClass {

  static final RegisteredApplicationService <warning descr="Application service must not be assigned to a static final field">myAppService1</warning> = RegisteredApplicationService.getInstance();

  static final RegisteredApplicationService <warning descr="Application service must not be assigned to a static final field">myAppService2</warning> = ApplicationManager.getApplication().getService(RegisteredApplicationService.class);

  // non-final
  static RegisteredApplicationService myAppService3 = RegisteredApplicationService.getInstance();

  // not an application service
  static final RegisteredProjectService myProjectService = new RegisteredProjectService();

}
