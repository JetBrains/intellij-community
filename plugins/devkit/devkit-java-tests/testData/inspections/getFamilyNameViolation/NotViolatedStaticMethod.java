import com.intellij.codeInspection.QuickFix;

class MyQuickFix implements QuickFix {

  String someField;

  public String getName() {
    return someField;
  };

  public String getFamilyName() {
    return getNameStatic() + "123";
  };

  static String getNameStatic() {
    return "Static";
  }


}