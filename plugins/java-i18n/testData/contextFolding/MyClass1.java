import java.util.ResourceBundle;

public class MyClass {
  private final static String BUNDLE_NAME = "i18n";
  private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

  public static String getMessage(@org.jetbrains.annotations.PropertyKey(resourceBundle = BUNDLE_NAME) String key, Object... args) {
    return java.text.MessageFormat.format(BUNDLE.getString(key), args);
  }

  public static void main(String[] args) {
    int i = 0;
    i++;
    System.out.println(i);
    <selection>MyClass.getMessage("com.example.welcomeMessage3", "our App", BUNDLE_NAME)</selection>;
  }
}