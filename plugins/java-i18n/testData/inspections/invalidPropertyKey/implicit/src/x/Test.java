package x;
import org.jetbrains.annotations.PropertyKey;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

class IBundle {

  public static String message(@org.jetbrains.annotations.NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                               @org.jetbrains.annotations.NotNull Object... params) {
    return "";
  }

  @NonNls
  public static final String BUNDLE = "x.Foo";
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle(bundleClassName);
}

public class Test {
  String s0 = IBundle.message("defaultKey");

  String ss4 = IBundle.message("with.params", 1, 2, 3);

  String f1(@PropertyKey(resourceBundle = IBundle.BUNDLE) String s, Object...params) {return "";}
  String f2(@PropertyKey(resourceBundle = IBundle.BUNDLE) String s) {return "";}
}
