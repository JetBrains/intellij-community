import org.jetbrains.annotations.PropertyKey;
import java.util.ResourceBundle;

public class MyClass {
  private final static String BUNDLE_NAME = "i18n";
  private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

  public static String getMessage(@PropertyKey(resourceBundle = BUNDLE_NAME) String key, Object... args) {
    return java.text.MessageFormat.format(BUNDLE.getString(key), args);
  }

  public static void main(String[] args) {
    getMessage("com.example.welcomeMessage2", "our App", BUNDLE_NAME);
  }
}