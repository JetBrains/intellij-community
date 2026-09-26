import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class UnsafeVisitReturnStatementUsedInJavaRecursiveElementWalkingVisitor {

  public void methodUsingUnsafeVisitReturnStatementAndNoSafeMethodsPresent(PsiElement element) {
    element.accept(new <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">JavaRecursiveElementWalkingVisitor</warning>() {
      @Override
      public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
        // do something
      }
    });
  }

  public void methodUsingUnsafeVisitReturnStatementButVisitClassPresent(PsiElement element) {
    element.accept(new <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">JavaRecursiveElementWalkingVisitor</warning>() {
      @Override
      public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
        // do something
      }

      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        // do something
      }
    });
  }

  public void methodUsingUnsafeVisitReturnStatementButVisitLambdaExpressionPresent(PsiElement element) {
    element.accept(new <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">JavaRecursiveElementWalkingVisitor</warning>() {
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

class <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">UnsafeVisitReturnStatementWalkingVisitor</warning> extends JavaRecursiveElementWalkingVisitor {
  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    // do something
  }
}

class <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">UnsafeVisitReturnStatementWalkingVisitorWithVisitLambdaExpression</warning> extends JavaRecursiveElementWalkingVisitor {
  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    // do something
  }

  @Override
  public void visitLambdaExpression(PsiLambdaExpression expression) {
    // do something
  }
}


class <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">UnsafeVisitReturnStatementWalkingVisitorWithVisitClass</warning> extends JavaRecursiveElementWalkingVisitor {
  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    // do something
  }

  @Override
  public void visitClass(@NotNull PsiClass aClass) {
    // do something
  }
}
