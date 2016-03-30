import com.intellij.codeInspection.QuickFix;

class MyQuickFix implements QuickFix {

  String someField;

  public String getName() {
    return "some name";
  };

  public String <warning descr="QuickFix's getFamilyName() implementation must not depend on a specific context">getFamilyName</warning>() {
    return someField + getName() + "123";
  };


}