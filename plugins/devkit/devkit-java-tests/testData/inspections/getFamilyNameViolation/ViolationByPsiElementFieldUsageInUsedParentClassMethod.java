import com.intellij.codeInspection.QuickFix;
import com.intellij.psi.PsiElement;

abstract class MyQuickFixBase implements QuickFix {

  String someField;
  PsiElement myElement;

  public String getName() {
    return someField;
  }

  protected String getValueOfMyElement() {
    return String.valueOf(myElement); // violation
  }
}

class MyQuickFix extends MyQuickFixBase {

  public String <warning descr="QuickFix's getFamilyName() implementation must not depend on a specific context">getFamilyName</warning>() {
    return "error is here: " + getValueOfMyElement();
  }

}
