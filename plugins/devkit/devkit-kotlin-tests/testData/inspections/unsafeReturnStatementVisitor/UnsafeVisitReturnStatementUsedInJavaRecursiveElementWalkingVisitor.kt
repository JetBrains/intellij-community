import com.intellij.psi.*

class UnsafeVisitReturnStatementUsedInJavaRecursiveElementWalkingVisitor {
  fun methodUsingUnsafeVisitReturnStatementAndNoSafeMethodsPresent(element: PsiElement) {
    element.accept(<warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">object</warning> : JavaRecursiveElementWalkingVisitor() {
      override fun visitReturnStatement(statement: PsiReturnStatement) {
        // do something
      }
    })
  }

  fun methodUsingUnsafeVisitReturnStatementButVisitClassPresent(element: PsiElement) {
    element.accept(<warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">object</warning> : JavaRecursiveElementWalkingVisitor() {
      override fun visitReturnStatement(statement: PsiReturnStatement) {
        // do something
      }

      override fun visitClass(aClass: PsiClass) {
        // do something
      }
    })
  }

  fun methodUsingUnsafeVisitReturnStatementButVisitLambdaExpressionPresent(element: PsiElement) {
    element.accept(<warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">object</warning> : JavaRecursiveElementWalkingVisitor() {
      override fun visitReturnStatement(statement: PsiReturnStatement) {
        // do something
      }

      override fun visitLambdaExpression(expression: PsiLambdaExpression) {
        // do something
      }
    })
  }
}

class <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">UnsafeVisitReturnStatementWalkingVisitor</warning> : JavaRecursiveElementWalkingVisitor() {
  override fun visitReturnStatement(statement: PsiReturnStatement) {
    // do something
  }
}

class <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">UnsafeVisitReturnStatementWalkingVisitorWithVisitLambdaExpression</warning> : JavaRecursiveElementWalkingVisitor() {
  override fun visitReturnStatement(statement: PsiReturnStatement) {
    // do something
  }

  override fun visitLambdaExpression(expression: PsiLambdaExpression) {
    // do something
  }
}

class <warning descr="Recursive visitors with 'visitReturnStatement' most probably should specifically process anonymous/local classes ('visitClass') and lambda expressions ('visitLambdaExpression')">UnsafeVisitReturnStatementWalkingVisitorWithVisitClass</warning> : JavaRecursiveElementWalkingVisitor() {
  override fun visitReturnStatement(statement: PsiReturnStatement) {
    // do something
  }

  override fun visitClass(aClass: PsiClass) {
    // do something
  }
}
