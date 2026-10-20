import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiMethod;

class PsiMethodAndAstNodeCall {
  void testMethod(PsiMethod method, ASTNode node) {
    methodHelper(method);
    nodeHelper(node);
  }

  void methodHelper(PsiMethod method) {
    method.getBody();
  }

  void nodeHelper(ASTNode node) {
    node.getElementType();
  }
}

