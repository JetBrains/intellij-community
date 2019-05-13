import com.intellij.codeInspection.QuickFix;

class MyQuickFix implements QuickFix {

  public String getName() {
    return "that fix do some fix";
  };

  public String getFamilyName() {
    return getName();
  };


}