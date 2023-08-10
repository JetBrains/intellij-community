import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class UnsafeVisitReturnStatementUsedInJavaRecursiveElementVisitor {

  public void methodUsingUnsafeVisitReturnStatementAndNoSafeMethodsPresent1(PsiElement element) {
    element.accept(new <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">JavaRecursiveElementVisitor</warning>() {
      @Override
      public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
        // do something
      }
    });
  }

  public void methodUsingUnsafeVisitReturnStatementButVisitClassPresent1(PsiElement element) {
    element.accept(new <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">JavaRecursiveElementVisitor</warning>() {
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
    element.accept(new <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">JavaRecursiveElementVisitor</warning>() {
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

class <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">UnsafeVisitReturnStatementVisitor</warning> extends JavaRecursiveElementVisitor {
  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    // do something
  }
}

class <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">UnsafeVisitReturnStatementVisitorWithVisitLambdaExpression</warning> extends JavaRecursiveElementVisitor {
  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    // do something
  }

  @Override
  public void visitLambdaExpression(PsiLambdaExpression expression) {
    // do something
  }
}

class <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">UnsafeVisitReturnStatementVisitorWithVisitClass</warning> extends JavaRecursiveElementVisitor {
  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    // do something
  }

  @Override
  public void visitClass(@NotNull PsiClass aClass) {
    // do something
  }
}
