import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import serviceDeclarations.RegisteredProjectService;
import serviceDeclarations.RegisteredApplicationService;

class MyClass {

  <warning descr="Application service must not be assigned to a static final field">static final RegisteredApplicationService myAppService1 = RegisteredApplicationService.getInstance();</warning>

  <warning descr="Application service must not be assigned to a static final field">static final RegisteredApplicationService myAppService2 = ApplicationManager.getApplication().getService(RegisteredApplicationService.class);</warning>

  // non-final
  static RegisteredApplicationService myAppService3 = RegisteredApplicationService.getInstance();

  // not an application service
  static final RegisteredProjectService myProjectService = new RegisteredProjectService();

}
