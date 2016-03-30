import com.intellij.codeInspection.QuickFix;

class MyQuickFix implements QuickFix {

  String someField;

  public String getName() {
    return someField;
  };

  public String <warning descr="QuickFix's getFamilyName() implementation must not depend on a specific context">getFamilyName</warning>() {
    return getName() + "123";
  };


}