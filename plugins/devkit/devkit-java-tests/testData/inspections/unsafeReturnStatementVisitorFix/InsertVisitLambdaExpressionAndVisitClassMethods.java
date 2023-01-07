import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class InsertVisitLambdaExpressionAndVisitClassMethods {

  public void method(PsiElement element) {
    element.accept(new <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">Java<caret>RecursiveElementWalkingVisitor</warning>() {
        @Override
        public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
          // do something
        }
    });
  }

}
