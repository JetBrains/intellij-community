package x;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

public class IBundle {

  public static String message(@org.jetbrains.annotations.NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                               @org.jetbrains.annotations.NotNull Object... params) {
    return "";
  }

  @NonNls
  public static final String BUNDLE = "x.Bu";
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle(bundleClassName);
}
