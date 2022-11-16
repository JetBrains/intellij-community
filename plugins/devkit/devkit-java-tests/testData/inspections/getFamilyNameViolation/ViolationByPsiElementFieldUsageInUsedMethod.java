import com.intellij.codeInspection.QuickFix;
import com.intellij.psi.PsiElement;

class MyQuickFix implements QuickFix {

  String someField;
  PsiElement myElement;

  public String getName() {
    return someField;
  }

  public String <warning descr="QuickFix's getFamilyName() implementation must not depend on a specific context">getFamilyName</warning>() {
    return "error is here: " + getValueOfMyElement();
  }

  private String getValueOfMyElement() {
    return String.valueOf(myElement); // violation
  }
}
