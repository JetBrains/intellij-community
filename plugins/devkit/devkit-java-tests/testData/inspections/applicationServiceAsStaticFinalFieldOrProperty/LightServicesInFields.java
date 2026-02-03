import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import serviceDeclarations.*;


class MyClass {

  static final LightServiceAppAndProjectLevelAnnotation <warning descr="Application service must not be assigned to a static final field">myAppService1</warning> = LightServiceAppAndProjectLevelAnnotation.getInstance();

  private static final LightServiceAppLevelAnnotation <warning descr="Application service must not be assigned to a static final field">myAppService2</warning> = ApplicationManager.getApplication().getService(LightServiceAppLevelAnnotation.class);

  public static final LightServiceEmptyAnnotation <warning descr="Application service must not be assigned to a static final field">myAppService4</warning> = LightServiceEmptyAnnotation.getInstance();

  // not final
  static LightServiceAppAndProjectLevelAnnotation myAppService5 = LightServiceAppAndProjectLevelAnnotation.getInstance();

  // not static
  final LightServiceAppLevelAnnotation myAppService6 = LightServiceAppLevelAnnotation.getInstance();

  // not an application service
  static final LightServiceProjecLevelAnnotation myProjectService = new LightServiceProjecLevelAnnotation();

}
