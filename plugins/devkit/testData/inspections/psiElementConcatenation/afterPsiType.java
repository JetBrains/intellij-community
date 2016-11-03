// "Use 'getCanonicalText' call" "true"
import com.intellij.psi.*;
import com.intellij.psi.util.*;

public class PsiElementConcatenationText {
  public PsiExpression test(PsiElementFactory factory, PsiType type, PsiElement element) {
    return factory.createExpressionFromText("(Object)"+ type.getCanonicalText(), element);
  }
}
