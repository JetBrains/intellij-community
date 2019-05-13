package x;

import java.lang.String;
import org.jetbrains.annotations.PropertyKey;
import java.util.ResourceBundle;

public class Test {

  private final String keyNameField = "with.params";

  void m() {
    String sss = IBundle.message(keyNameField, "");
  }

}

class IBundle {

  public static String message(@org.jetbrains.annotations.NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                               @org.jetbrains.annotations.NotNull Object... params) {
    return "";
  }

  @NonNls
  public static final String BUNDLE = "x.Bu";
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle(bundleClassName);
}