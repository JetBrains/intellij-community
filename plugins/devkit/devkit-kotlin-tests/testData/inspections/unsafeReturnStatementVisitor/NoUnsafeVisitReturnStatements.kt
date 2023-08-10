import com.intellij.psi.*

class NoUnsafeVisitReturnStatements {
  // it shouldn't be reported as JavaElementVisitor is not recursive (JavaRecursiveElementWalkingVisitor/JavaRecursiveElementVisitor)
  fun methodUsingUnsupportedVisitorImplementation1(element: PsiElement) {
    element.accept(object : JavaElementVisitor() {
      override fun visitReturnStatement(statement: PsiReturnStatement) {
        // do something
      }
    })
  }

  // it shouldn't be reported as it not JavaRecursiveElementWalkingVisitor/JavaRecursiveElementVisitor
  fun methodUsingUnsupportedVisitorImplementation2(element: PsiElement) {
    element.accept(object : MyCustomVisitor() {
      override fun visitReturnStatement(statement: PsiReturnStatement) {
        // do something
      }
    })
  }

  // it shouldn't be reported as it implements visitClass and visitLambdaExpression
  fun methodWithImplementationContainingVisitClassAndVisitLambdaExpressionMethods1(element: PsiElement) {
    element.accept(object : JavaRecursiveElementVisitor() {
      override fun visitReturnStatement(statement: PsiReturnStatement) {
        // do something
      }

      override fun visitClass(aClass: PsiClass) {
        // do something
      }

      override fun visitLambdaExpression(expression: PsiLambdaExpression) {
        // do something
      }
    })
  }

  // it shouldn't be reported as it implements visitClass and visitLambdaExpression
  fun methodWithImplementationContainingVisitClassAndVisitLambdaExpressionMethods2(element: PsiElement) {
    element.accept(object : JavaRecursiveElementWalkingVisitor() {
      override fun visitReturnStatement(statement: PsiReturnStatement) {
        // do something
      }

      override fun visitClass(aClass: PsiClass) {
        // do something
      }

      override fun visitLambdaExpression(expression: PsiLambdaExpression) {
        // do something
      }
    })
  }
}

open class MyCustomVisitor : PsiElementVisitor() {
  open fun visitReturnStatement(statement: PsiReturnStatement) {
    // do something
  }
} // JavaElementVisitor is safe

class SafeVisitReturnStatementVisitor : JavaElementVisitor() {
  override fun visitReturnStatement(statement: PsiReturnStatement) {
    // do something
  }
}
