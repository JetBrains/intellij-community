import com.intellij.psi.impl.PsiElementBase;

class PsiElementBaseCall {
  void testMethod(PsiElementBase element) {
    helper(element);
  }

  void helper(PsiElementBase element) {
    element.getText();
  }
}
