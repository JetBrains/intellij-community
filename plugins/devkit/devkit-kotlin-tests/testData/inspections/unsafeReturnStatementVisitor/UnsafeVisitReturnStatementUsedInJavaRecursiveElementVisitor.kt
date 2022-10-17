import com.intellij.psi.*

class UnsafeVisitReturnStatementUsedInJavaRecursiveElementVisitor {
  fun methodUsingUnsafeVisitReturnStatementAndNoSafeMethodsPresent1(element: PsiElement) {
    element.accept(<warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">object</warning> : JavaRecursiveElementVisitor() {
      override fun visitReturnStatement(statement: PsiReturnStatement) {
        // do something
      }
    })
  }

  fun methodUsingUnsafeVisitReturnStatementButVisitClassPresent1(element: PsiElement) {
    element.accept(<warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">object</warning> : JavaRecursiveElementVisitor() {
      override fun visitReturnStatement(statement: PsiReturnStatement) {
        // do something
      }

      override fun visitClass(aClass: PsiClass) {
        // do something
      }
    })
  }

  fun methodUsingUnsafeVisitReturnStatementButVisitLambdaExpressionPresent(element: PsiElement) {
    element.accept(<warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">object</warning> : JavaRecursiveElementVisitor() {
      override fun visitReturnStatement(statement: PsiReturnStatement) {
        // do something
      }

      override fun visitLambdaExpression(expression: PsiLambdaExpression) {
        // do something
      }
    })
  }
}

class <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">UnsafeVisitReturnStatementVisitor</warning> : JavaRecursiveElementVisitor() {
  override fun visitReturnStatement(statement: PsiReturnStatement) {
    // do something
  }
}

class <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">UnsafeVisitReturnStatementVisitorWithVisitLambdaExpression</warning> : JavaRecursiveElementVisitor() {
  override fun visitReturnStatement(statement: PsiReturnStatement) {
    // do something
  }

  override fun visitLambdaExpression(expression: PsiLambdaExpression) {
    // do something
  }
}

class <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">UnsafeVisitReturnStatementVisitorWithVisitClass</warning> : JavaRecursiveElementVisitor() {
  override fun visitReturnStatement(statement: PsiReturnStatement) {
    // do something
  }

  override fun visitClass(aClass: PsiClass) {
    // do something
  }
}
