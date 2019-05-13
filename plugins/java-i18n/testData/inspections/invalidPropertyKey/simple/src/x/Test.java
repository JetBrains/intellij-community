package x;
import org.jetbrains.annotations.PropertyKey;
class Test {
  String s0 = IBundle.message("defaultKey");
  String s1 = IBundle.message("frenchKey");
  String s2 = IBundle.message("bollocks");
  String s3 = IBundle.message("with.params", "a", "b");
  
  String computable = IBundle.message(this + "bollocks");
  String computable2 = IBundle.message((("defaultKey")));
  String weird = IBundle.message("weird", 0,1,2);
  boolean b = "aaa".contains("with.params");

  String ss1 = f1("with.params"); 
  String ss2 = f1("with.params", "", "", "");
  String ss3 = f2("with.params");
  String ss4 = IBundle.message("with.params", new Object[3]); // don't check if array passed

  private static final String CONST = ".params";

  String f1(@PropertyKey(resourceBundle = IBundle.BUNDLE) String s, Object...params) {return "";}
  String f2(@PropertyKey(resourceBundle = IBundle.BUNDLE) String s) {return "";}
  void f3(@PropertyKey(resourceBundle = "invalid") String s, int i) {
    IBundle.message(s + ".params");
    IBundle.message(s + CONST);
    IBundle.message(s == null ? CONST : "invalid");
    IBundle.message(((("invalid"))));
    IBundle.message((((CONST))));
    String pattern;
    if (i == 0) {
      pattern = "invalid";
    }
    else if (i == 1) {
      pattern = s + ".params";
    }
    else if (i == 2) {
      pattern = "defaultKey";
    }
    else {
      pattern = i > 10
                ? "invalid"
                : "invalid";
    }
    f2(pattern);
  }
}
