import com.intellij.codeInspection.QuickFix;

class MyQuickFix implements QuickFix {

  static String someField = "Hello";

  public String getName() {
    return "some name";
  };

  public String getFamilyName() {
    return someField + getName() + "123";
  };


}