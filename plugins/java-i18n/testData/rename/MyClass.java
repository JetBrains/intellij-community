import org.jetbrains.annotations.PropertyKey;
import java.util.ResourceBundle;

public class MyClass {
  private final static String BUNDLE_NAME = "i18n";
  private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

  public static void main(String[] args) {
    System.out.print(getMessage("com.example.localization.welcomeMessage"));
  }


  private static String getMessage(@PropertyKey(resourceBundle = BUNDLE_NAME) String key) {
    return BUNDLE.getString(key);
  }
}