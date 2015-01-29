package x;
import org.jetbrains.annotations.PropertyKey;
import java.util.ResourceBundle;

public class Test {
  /**
   * exists only in "Bu" resource bundle => can resolve
   */
  String s3 = IBundle.message("with.params2", "a");

  /**
   * no way to determine source of "with.params" property, because this key exists in both of resource bundles
   */
  String ss1 = f1("with.params");
  String ss2 = f1("with.params", "", "", "");
  String ss3 = f2("with.params");
  String ss4 = IBundle.message("with.params", new Object[3]); // don't check if array passed

  String f1(@PropertyKey(resourceBundle = IBundle.BUNDLE) String s, Object...params) {return "";}
  String f2(@PropertyKey(resourceBundle = IBundle.BUNDLE) String s) {return "";}
}

class IBundle {

  public static String message(@org.jetbrains.annotations.NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                               @org.jetbrains.annotations.NotNull Object... params) {
    return "";
  }

  /*
   * not final field -- there is no way to determine value
   */
  @NonNls
  public static String BUNDLE = "x.Baz";
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle(bundleClassName);
}

