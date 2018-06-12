import com.intellij.openapi.components.ApplicationComponent;

public class <warning descr="Application Component is not registered in plugin.xml">Unregistered<caret>ApplicationComponent</warning>
  implements ApplicationComponent, UnregisteredApplicationComponentInterface {

  public static UnregisteredApplicationComponentInterface getInstance() {
    return null;
  }

  public static class <warning descr="Application Component is not registered in plugin.xml">InnerStaticClassApplicationContext</warning>
  implements ApplicationComponent {}

  public class InnerClassApplicationContextIsNotChecked implements ApplicationComponent {}

}