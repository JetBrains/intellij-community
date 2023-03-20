import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import serviceDeclarations.RegisteredProjectService;
import serviceDeclarations.RegisteredApplicationService;

class MyClass {

  static final <warning descr="Application service must not be assigned to a static final field">RegisteredApplicationService</warning> myAppService1 = RegisteredApplicationService.getInstance();

  static final <warning descr="Application service must not be assigned to a static final field">RegisteredApplicationService</warning> myAppService2 = ApplicationManager.getApplication().getService(RegisteredApplicationService.class);

  // non-final
  static RegisteredApplicationService myAppService3 = RegisteredApplicationService.getInstance();

  // not an application service
  static final RegisteredProjectService myProjectService = new RegisteredProjectService();

}
