import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import serviceDeclarations.*;


class MyClass {

  <warning descr="Application service must not be assigned to a static final field">static final LightServiceAppAndProjectLevelAnnotation myAppService1 = LightServiceAppAndProjectLevelAnnotation.getInstance();</warning>

  <warning descr="Application service must not be assigned to a static final field">private static final LightServiceAppLevelAnnotation myAppService2 = ApplicationManager.getApplication().getService(LightServiceAppLevelAnnotation.class);</warning>

  <warning descr="Application service must not be assigned to a static final field">static final LightServiceDefaultAnnotation myAppService3 = LightServiceDefaultAnnotation.getInstance();</warning>

  <warning descr="Application service must not be assigned to a static final field">public static final LightServiceEmptyAnnotation myAppService4 = LightServiceEmptyAnnotation.getInstance();</warning>

  // not final
  static LightServiceAppAndProjectLevelAnnotation myAppService5 = LightServiceAppAndProjectLevelAnnotation.getInstance();

  // not static
  final LightServiceAppLevelAnnotation myAppService6 = LightServiceAppLevelAnnotation.getInstance();

  // not an application service
  static final LightServiceProjecLevelAnnotation myProjectService = new LightServiceProjecLevelAnnotation();

}
