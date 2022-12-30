import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class InsertVisitLambdaExpressionMethod {

  public void method(PsiElement element) {
    element.accept(new <warning descr="Recursive visitors which visit return statements most probably should specifically process anonymous/local classes as well as lambda expressions">JavaRecursive<caret>ElementVisitor</warning>() {
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
