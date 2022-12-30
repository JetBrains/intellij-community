import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class UnsafeVisitReturnStatementUsedInJavaRecursiveElementWalkingVisitor {

  public void methodUsingUnsafeVisitReturnStatementAndNoSafeMethodsPresent(PsiElement element) {
    element.accept(new <warning descr="Recursive visitors which visit return statements most probably should specifically process anonymous/local classes as well as lambda expressions">JavaRecursiveElementWalkingVisitor</warning>() {
      @Override
      public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
        // do something
      }
    });
  }

  public void methodUsingUnsafeVisitReturnStatementButVisitClassPresent(PsiElement element) {
    element.accept(new <warning descr="Recursive visitors which visit return statements most probably should specifically process anonymous/local classes as well as lambda expressions">JavaRecursiveElementWalkingVisitor</warning>() {
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
    element.accept(new <warning descr="Recursive visitors which visit return statements most probably should specifically process anonymous/local classes as well as lambda expressions">JavaRecursiveElementWalkingVisitor</warning>() {
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

<warning descr="Recursive visitors which visit return statements most probably should specifically process anonymous/local classes as well as lambda expressions">class UnsafeVisitReturnStatementWalkingVisitor extends JavaRecursiveElementWalkingVisitor</warning> {
  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    // do something
  }
}

<warning descr="Recursive visitors which visit return statements most probably should specifically process anonymous/local classes as well as lambda expressions">class UnsafeVisitReturnStatementWalkingVisitorWithVisitLambdaExpression extends JavaRecursiveElementWalkingVisitor</warning> {
  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    // do something
  }

  @Override
  public void visitLambdaExpression(PsiLambdaExpression expression) {
    // do something
  }
}


<warning descr="Recursive visitors which visit return statements most probably should specifically process anonymous/local classes as well as lambda expressions">class UnsafeVisitReturnStatementWalkingVisitorWithVisitClass extends JavaRecursiveElementWalkingVisitor</warning> {
  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    // do something
  }

  @Override
  public void visitClass(@NotNull PsiClass aClass) {
    // do something
  }
}
