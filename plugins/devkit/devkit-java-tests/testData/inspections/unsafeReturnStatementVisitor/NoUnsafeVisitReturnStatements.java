import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class NoUnsafeVisitReturnStatements {

  // it shouldn't be reported as JavaElementVisitor is not recursive (JavaRecursiveElementWalkingVisitor/JavaRecursiveElementVisitor)
  public void methodUsingUnsupportedVisitorImplementation1(PsiElement element) {
    element.accept(new JavaElementVisitor() {
      @Override
      public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
        // do something
      }
    });
  }

  // it shouldn't be reported as it not JavaRecursiveElementWalkingVisitor/JavaRecursiveElementVisitor
  public void methodUsingUnsupportedVisitorImplementation2(PsiElement element) {
    element.accept(new MyCustomVisitor() {
      @Override
      public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
        // do something
      }
    });
  }

  // it shouldn't be reported as it implements visitClass and visitLambdaExpression
  public void methodWithImplementationContainingVisitClassAndVisitLambdaExpressionMethods1(PsiElement element) {
    element.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
        // do something
      }

      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        // do something
      }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        // do something
      }
    });
  }

  // it shouldn't be reported as it implements visitClass and visitLambdaExpression
  public void methodWithImplementationContainingVisitClassAndVisitLambdaExpressionMethods2(PsiElement element) {
    element.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
        // do something
      }

      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        // do something
      }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        // do something
      }
    });
  }

}

class MyCustomVisitor extends PsiElementVisitor {

  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    // do something
  }

}

// JavaElementVisitor is safe
class SafeVisitReturnStatementVisitor extends JavaElementVisitor {
  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    // do something
  }
}
