import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class InsertVisitLambdaExpressionAndVisitClassMethods {

  public void method(PsiElement element) {
    element.accept(new <warning descr="Recursive visitors which visit return statements most probably should specifically process anonymous/local classes as well as lambda expressions">Java<caret>RecursiveElementWalkingVisitor</warning>() {
        @Override
        public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
          // do something
        }
    });
  }

}
