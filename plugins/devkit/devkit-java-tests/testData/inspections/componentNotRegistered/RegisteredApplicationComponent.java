import com.intellij.openapi.components.ApplicationComponent;

public class RegisteredApplicationComponent
  implements ApplicationComponent {

  public static class InnerStaticClassApplicationContext
    implements ApplicationComponent {
  }
}