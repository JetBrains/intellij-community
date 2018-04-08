// "Use 'getText' call" "true"
import com.intellij.psi.*;
import com.intellij.psi.util.*;

public class PsiElementConcatenationText {
  public PsiExpression test(PsiElementFactory factory, PsiElement element) {
    String s = "(Object)"+elem<caret>ent;
    String l = "x -> "+s;
    return factory.createExpressionFromText("("+l+")", element);
  }
}
