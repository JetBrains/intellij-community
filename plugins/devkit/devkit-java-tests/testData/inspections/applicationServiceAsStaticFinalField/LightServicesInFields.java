import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import serviceDeclarations.*;


class MyClass {

  static final <warning descr="Application service must not be assigned to a static final field">LightServiceAppAndProjectLevelAnnotation</warning> myAppService1 = LightServiceAppAndProjectLevelAnnotation.getInstance();

  private static final <warning descr="Application service must not be assigned to a static final field">LightServiceAppLevelAnnotation</warning> myAppService2 = ApplicationManager.getApplication().getService(LightServiceAppLevelAnnotation.class);

  static final <error descr="Cannot resolve symbol 'LightServiceCoroutineConstructor'">LightServiceCoroutineConstructor</error> myAppService3 = <error descr="Cannot resolve symbol 'LightServiceCoroutineConstructor'">LightServiceCoroutineConstructor</error>.getInstance();

  public static final <error descr="Cannot resolve symbol 'LightServiceEmptyAnnotationEmptyConstructor'">LightServiceEmptyAnnotationEmptyConstructor</error> myAppService4 = <error descr="Cannot resolve symbol 'LightServiceEmptyAnnotationEmptyConstructor'">LightServiceEmptyAnnotationEmptyConstructor</error>.getInstance();

  static final <error descr="Cannot resolve symbol 'LightServiceEmptyConstructor'">LightServiceEmptyConstructor</error> myAppService5 = ApplicationManager.getApplication().getService(<error descr="Cannot resolve symbol 'LightServiceEmptyConstructor'">LightServiceEmptyConstructor</error>.class);

  // not final
  static LightServiceAppAndProjectLevelAnnotation myAppService6 = LightServiceAppAndProjectLevelAnnotation.getInstance();

  // not static
  final LightServiceAppLevelAnnotation myAppService7 = LightServiceAppLevelAnnotation.getInstance();

  // not an application service
  static final LightServiceProjecLevelAnnotation myProjectService = new LightServiceProjecLevelAnnotation();

}
