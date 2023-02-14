import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class InsertVisitClassMethod {

  public void method(PsiElement element) {
    element.accept(new <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">Java<caret>RecursiveElementVisitor</warning>() {
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
