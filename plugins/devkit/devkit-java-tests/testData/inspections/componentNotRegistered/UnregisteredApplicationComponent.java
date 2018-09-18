import com.intellij.openapi.components.BaseComponent;

public class <warning descr="Component is not registered in plugin.xml">UnregisteredApplicationComponent</warning>
  implements BaseComponent, UnregisteredApplicationComponentInterface {

  public static UnregisteredApplicationComponentInterface getInstance() {
    return null;
  }

  public static class <warning descr="Component is not registered in plugin.xml">InnerStaticClassApplicationContext</warning>
  implements BaseComponent {}

  public class InnerClassApplicationContextIsNotChecked implements BaseComponent {}

}