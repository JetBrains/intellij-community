import com.intellij.psi.PsiElement;

interface PsiNamed extends PsiElement {
  String getName();
}

class PsiElementMethodCall {
  void testMethod(PsiNamed element) {
    element.getName();
    helper(element);
  }

  void helper(PsiNamed element) {
    element.getText();
  }
}
