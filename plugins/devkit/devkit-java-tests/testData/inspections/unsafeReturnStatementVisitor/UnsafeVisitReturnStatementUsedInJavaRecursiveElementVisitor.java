import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class UnsafeVisitReturnStatementUsedInJavaRecursiveElementVisitor {

  public void methodUsingUnsafeVisitReturnStatementAndNoSafeMethodsPresent1(PsiElement element) {
    element.accept(new <warning descr="Recursive visitors which visit return statements most probably should specifically process anonymous/local classes as well as lambda expressions">JavaRecursiveElementVisitor</warning>() {
      @Override
      public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
        // do something
      }
    });
  }

  public void methodUsingUnsafeVisitReturnStatementButVisitClassPresent1(PsiElement element) {
    element.accept(new <warning descr="Recursive visitors which visit return statements most probably should specifically process anonymous/local classes as well as lambda expressions">JavaRecursiveElementVisitor</warning>() {
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
    element.accept(new <warning descr="Recursive visitors which visit return statements most probably should specifically process anonymous/local classes as well as lambda expressions">JavaRecursiveElementVisitor</warning>() {
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

<warning descr="Recursive visitors which visit return statements most probably should specifically process anonymous/local classes as well as lambda expressions">class UnsafeVisitReturnStatementVisitor extends JavaRecursiveElementVisitor</warning> {
  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    // do something
  }
}

<warning descr="Recursive visitors which visit return statements most probably should specifically process anonymous/local classes as well as lambda expressions">class UnsafeVisitReturnStatementVisitorWithVisitLambdaExpression extends JavaRecursiveElementVisitor</warning> {
  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    // do something
  }

  @Override
  public void visitLambdaExpression(PsiLambdaExpression expression) {
    // do something
  }
}

<warning descr="Recursive visitors which visit return statements most probably should specifically process anonymous/local classes as well as lambda expressions">class UnsafeVisitReturnStatementVisitorWithVisitClass extends JavaRecursiveElementVisitor</warning> {
  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    // do something
  }

  @Override
  public void visitClass(@NotNull PsiClass aClass) {
    // do something
  }
}
