import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class InsertVisitClassMethod {

  public void method(PsiElement element) {
    element.accept(new Java<caret>RecursiveElementVisitor() {
        @Override
        public void visitClass(PsiClass aClass) {
        }

        @Override
        public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
          // do something
        }

        @Override
        public void visitLambdaExpression(PsiLambdaExpression expression) {
          // do something
        }
    });
  }

}
