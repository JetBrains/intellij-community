import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class InsertVisitLambdaExpressionMethod {

  public void method(PsiElement element) {
    element.accept(new JavaRecursive<caret>ElementVisitor() {
        @Override
        public void visitLambdaExpression(PsiLambdaExpression expression) {
        }

        @Override
        public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
          // do something
        }

        @Override
        public void visitClass(PsiClass psiClass) {
          // do something
        }
    });
  }

}
