import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class InsertVisitLambdaExpressionAndVisitClassMethods {

  public void method(PsiElement element) {
    element.accept(new Java<caret>RecursiveElementWalkingVisitor() {
        @Override
        public void visitClass(PsiClass aClass) {
        }

        @Override
        public void visitLambdaExpression(PsiLambdaExpression expression) {
        }

        @Override
        public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
          // do something
        }
    });
  }

}
