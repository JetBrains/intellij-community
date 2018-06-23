// "Use 'getText' call" "true"
import com.intellij.psi.*;
import com.intellij.psi.util.*;

public class PsiElementConcatenationText {
  public PsiExpression test(PsiElementFactory factory, PsiElement element) {
    return factory.createExpressionFromText(element.getText(), element);
  }
}
