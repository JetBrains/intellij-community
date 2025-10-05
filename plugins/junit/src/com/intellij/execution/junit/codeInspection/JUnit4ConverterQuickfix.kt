// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.isInheritorOf
import com.intellij.execution.JUnitBundle
import com.intellij.jvm.analysis.quickFix.CompositeModCommandQuickFix
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.JAVA_LANG_CLASS
import com.intellij.psi.util.InheritanceUtil
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException
import com.intellij.refactoring.ui.ConflictsDialog
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.asSafely
import com.intellij.util.containers.MultiMap
import com.siyeh.ig.callMatcher.CallMatcher
import com.siyeh.ig.junit.JUnitCommonClassNames.*
import com.siyeh.ig.psiutils.TypeUtils
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.util.isInFinallyBlock
import org.jetbrains.uast.visitor.AbstractUastVisitor

internal class JUnit4ConverterQuickfix : LocalQuickFix {
  override fun getFamilyName(): String = JUnitBundle.message("jvm.inspections.junit4.converter.quickfix.name")

  override fun startInWriteAction(): Boolean = false

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    val junit3Class = previewDescriptor.psiElement.getUastParentOfType<UClass>() ?: return IntentionPreviewInfo.EMPTY
    performConversion(junit3Class)
    return IntentionPreviewInfo.DIFF
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(descriptor.psiElement)) return
    val junit3Class = descriptor.psiElement.getUastParentOfType<UClass>() ?: return
    val conflicts = findConflicts(junit3Class)

    val runnable = Runnable { WriteAction.run<RuntimeException> { performConversion(junit3Class) } }

    if (!conflicts.isEmpty) {
      if (ApplicationManager.getApplication().isUnitTestMode && !ConflictsInTestsException.isTestIgnore()) {
        throw ConflictsInTestsException(conflicts.values())
      }
      else if (!ConflictsDialog(junit3Class.javaPsi.project, conflicts, runnable).showAndGet()) return
    }
    runnable.run()
  }

  private fun performConversion(junit3Class: UClass) {
    for (method in junit3Class.methods) {
      val methodPtr = SmartPointerManager.createPointer(method.sourcePsi ?: continue)
      if (method.name.startsWith("test")) {
        addAnnotation(method, ORG_JUNIT_TEST)
      }
      else if (method.name == SETUP) {
        addAnnotation(method, ORG_JUNIT_BEFORE)
        transformSetUpOrTearDownMethod(method)
      }
      else if (method.name == TEARDOWN) {
        addAnnotation(method, ORG_JUNIT_AFTER)
        transformSetUpOrTearDownMethod(method)
      } else if (method.name == SUITE) {
        transformTesSuite(method)
      }
      methodPtr.element?.toUElementOfType<UMethod>()?.accept(AssertionsConverter())
    }
    junit3Class.javaPsi.extendsList?.referenceElements?.firstOrNull { it.qualifiedName == JUNIT_FRAMEWORK_TEST_CASE }?.delete()
  }

  private fun findConflicts(junit3Class: UClass): MultiMap<PsiElement, String> {
    val conflicts = MultiMap<PsiElement, String>()
    findNonMigratableTestSuiteConflicts(junit3Class)?.let { conflicts.putValue(it.first, it.second) }
    conflicts.putAllValues(findInheritedUsagesConflicts(junit3Class))
    conflicts.putAllValues(findSetupTeardownNameConflicts(junit3Class))
    return conflicts
  }

  private fun findInheritedUsagesConflicts(junit3Class: UClass): MultiMap<PsiElement, String> {
    val conflicts = MultiMap<PsiElement, String>()
    junit3Class.accept(object : AbstractUastVisitor() {
      override fun visitCallExpression(node: UCallExpression): Boolean {
        if (migratableMethodNames.contains(node.methodName)) return false
        if (node.kind == UastCallKind.CONSTRUCTOR_CALL) return false
        val method = node.resolveToUElementOfType<UMethod>() ?: return false
        if (migratableConstructorNames.contains(method.name)) return false
        if (method.isStatic) return false // filter out assertions, they will be converted later
        val containingMethodClass = method.javaPsi.containingClass ?: return false
        if (isAvailableAfterMigration(containingMethodClass.qualifiedName ?: return false)) return false
        val sourcePsi = node.sourcePsi ?: return false
        val expressionText = CommonRefactoringUtil.htmlEmphasize(sourcePsi.text)
        val classText = RefactoringUIUtil.getDescription(junit3Class.javaPsi, false)
        val availableSupers = method.javaPsi.findSuperMethods()
          .mapNotNull { it.containingClass?.qualifiedName }
          .filter { isAvailableAfterMigration(it) }
        val problem = if (availableSupers.isNotEmpty()) {
          JUnitBundle.message("jvm.inspections.junit4.converter.quickfix.conflict.semantics", expressionText, classText)
        } else {
          JUnitBundle.message("jvm.inspections.junit4.converter.quickfix.conflict.call.compile", expressionText, classText)
        }
        conflicts.putValue(node.sourcePsi, problem)
        return false
      }
    })
    return conflicts
  }

  private fun isAvailableAfterMigration(fqn: String): Boolean = !fqn.startsWith("junit.framework")

  private fun findNonMigratableTestSuiteConflicts(junit3Class: UClass): Pair<PsiElement, String>? {
    var conflict: Pair<PsiElement, String>? = null
    junit3Class.accept(object : AbstractUastVisitor() {
      override fun visitElement(node: UElement): Boolean = conflict != null

      override fun visitMethod(node: UMethod): Boolean {
        if (conflict != null) return true
        if (node.name == SUITE && !isMigratableTestSuite(node)) {
          val containingClass = node.getContainingUClass() ?: return false
          val classText = RefactoringUIUtil.getDescription(containingClass.javaPsi, false)
          conflict = node to JUnitBundle.message("jvm.inspections.junit4.converter.quickfix.conflict.suite", classText)
        }
        return true
      }

      private fun isMigratableTestSuite(method: UMethod): Boolean {
        var migratable = true
        method.uastBody?.accept(object : AbstractUastVisitor() {
          override fun visitElement(node: UElement): Boolean = !migratable

          override fun visitCallExpression(node: UCallExpression): Boolean {
            if (!migratable) return true
            when (node.kind) {
              UastCallKind.METHOD_CALL -> {
                when {
                  addTestMatcher.uCallMatches(node) -> {
                    val argumentExpr = node.valueArguments.first().getUCallExpression(searchLimit = 2) ?: return false
                    if (argumentExpr.isSuite()) return true
                    migratable = false
                  }
                  addTestSuiteMatcher.uCallMatches(node) -> { }
                  else -> migratable = false
                }
              }
              UastCallKind.CONSTRUCTOR_CALL -> {
                if (node.returnType?.isInheritorOf(JUNIT_FRAMEWORK_TEST_SUITE) == false) migratable = false
              }
              else -> migratable = false
            }
            return true
          }

          override fun visitReturnExpression(node: UReturnExpression): Boolean {
            if (!migratable) return true
            if (node.returnExpression?.getExpressionType()?.isInheritorOf(JUNIT_FRAMEWORK_TEST_SUITE) == false) migratable = false
            return false
          }
        })
        return migratable
      }
    })
    return conflict
  }

  fun UCallExpression.isSuite() = valueArgumentCount == 0
                                  && methodName == SUITE
                                  && TypeUtils.resolvedClassName(returnType) == JUNIT_FRAMEWORK_TEST

  private fun findSetupTeardownNameConflicts(junit3Class: UClass): MultiMap<PsiElement, String> {
    val conflicts = MultiMap<PsiElement, String>()
    junit3Class.accept(object : AbstractUastVisitor() {
      override fun visitMethod(node: UMethod): Boolean {
        if (node.name != TEARDOWN && node.name != SETUP) return true
        val superMethods = node.javaPsi.findSuperMethods().toMutableList()
        for (method in superMethods) {
          if (method.containingClass?.qualifiedName == JUNIT_FRAMEWORK_TEST_CASE) {
            superMethods.remove(method)
            break
          }
        }
        if (superMethods.isEmpty()) return true
        conflicts.putValue(node, JUnitBundle.message(
          "jvm.inspections.junit4.converter.quickfix.conflict.name", node.uastAnchor?.sourcePsi?.text
        ))
        return true
      }
    })
    return conflicts
  }

  private fun addAnnotation(aClass: PsiClass, fqn: String, vararg parameters: AnnotationAttributeRequest) {
    createAddAnnotationActions(aClass, annotationRequest(fqn, *parameters)).forEach {
      invoke(it, aClass.containingFile!!)
    }
  }

  private fun addAnnotation(method: UMethod, fqn: String, vararg parameters: AnnotationAttributeRequest) {
    createAddAnnotationActions(method.javaPsi, annotationRequest(fqn, *parameters)).forEach {
      invoke(it, method.sourcePsi?.containingFile!!)
    }
  }

  private fun invoke(action: IntentionAction, file: PsiFile) {
    PsiDocumentManager.getInstance(file.project).doPostponedOperationsAndUnblockDocument(file.viewProvider.document)
    action.invoke(file.project, null, file)
  }

  private fun transformSetUpOrTearDownMethod(method: UMethod) {
    val containingFile = method.sourcePsi?.containingFile ?: return
    CompositeModCommandQuickFix.performActions(createModifierActions(method.javaPsi, modifierRequest(JvmModifier.PUBLIC, true)), containingFile)
    CompositeModCommandQuickFix.performActions(createChangeOverrideActions(method.javaPsi, shouldBePresent = false), containingFile)
    val visitor = SuperCallRemoverVisitor(method.name)
    method.accept(visitor)
    visitor.applyModifications()
  }

  private fun transformTesSuite(method: UMethod) {
    val containingClass = method.javaPsi.containingClass ?: return
    val classValues = findAddedTestSuites(method).map { AnnotationAttributeValueRequest.ClassValue(it) }
    addAnnotation(
      containingClass,
      ORG_JUNIT_RUNNERS_SUITE_SUITE_CLASSES,
      arrayAttribute(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME, classValues)
    )
    addAnnotation(
      containingClass,
      ORG_JUNIT_RUNNER_RUN_WITH,
      classAttribute(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME, ORG_JUNIT_RUNNERS_SUITE)
    )
    method.sourcePsi?.delete()
  }

  private fun findAddedTestSuites(method: UMethod): List<String> {
    val classLiterals = mutableListOf<String>()
    method.uastBody?.accept(object : AbstractUastVisitor() {
      override fun visitCallExpression(node: UCallExpression): Boolean {
        when {
          addTestMatcher.uCallMatches(node) -> {
            val qualified = node.valueArguments.first().asSafely<UQualifiedReferenceExpression>()
            if(qualified?.selector?.asSafely<UCallExpression>()?.isSuite() == false) return false // don't show conflict in preview
            val receiver = qualified?.receiver.asSafely<UReferenceExpression>() ?: return false
            val suiteClass = receiver.resolve().toUElementOfType<UClass>()?.javaPsi
            val suiteFqn = if (InheritanceUtil.isInheritor(suiteClass, JUNIT_FRAMEWORK_TEST_CASE)) {
              suiteClass?.qualifiedName
            } else {
              suiteClass?.containingClass?.qualifiedName // receiver was companion object
            }
            classLiterals.add(suiteFqn ?: return false)
          }
          addTestSuiteMatcher.uCallMatches(node) -> {
            val type = node.valueArguments
              .first().getQualifiedChain()
              .first().asSafely<UClassLiteralExpression>()
              ?.type ?: return false
            classLiterals.add(TypeUtils.resolvedClassName(type) ?: return false)
          }
        }
        return false
      }
    })
    return classLiterals
  }

  private class SuperCallRemoverVisitor(private val methodName: String) : AbstractUastVisitor() {
    private val elementsToDelete = mutableListOf<PsiElement>()
    private val tryExpressions = mutableListOf<UTryExpression>()

    override fun visitSuperExpression(node: USuperExpression): Boolean {
      val qualifiedSuper = node.uastParent.asSafely<UQualifiedReferenceExpression>() ?: return false
      val selector = qualifiedSuper.selector.asSafely<UCallExpression>() ?: return false
      if (selector.methodName != methodName) return false
      if (qualifiedSuper.isInFinallyBlock()) { // tearDown methods are often in finally clauses
        val tryExpression = qualifiedSuper.getParentOfType(
          parentClass = UTryExpression::class.java,
          strict = true,
          terminators = arrayOf(UMethod::class.java)
        ) ?: return false
        val finallyClause = tryExpression.finallyClause ?: return false
        if (finallyClause.asSafely<UBlockExpression>()?.expressions?.size == 1) {
          val catchClauses = tryExpression.catchClauses
          if (catchClauses.isEmpty() && !tryExpression.hasResources) {
            tryExpressions.add(tryExpression)
          }
          else {
            finallyClause.sourcePsi?.let { elementsToDelete.add(it) }
          }
          return false
        }
      }
      qualifiedSuper.sourcePsi?.let { elementsToDelete.add(it) }
      return false
    }

    fun applyModifications() {
      for (tryExpression in tryExpressions) {
        val tryExprSrcPsi = tryExpression.sourcePsi ?: continue
        val tryClauseSrcPsi = tryExpression.tryClause.sourcePsi ?: continue
        val first = tryClauseSrcPsi.firstChild?.nextSibling
        val last = tryClauseSrcPsi.lastChild?.prevSibling
        tryExprSrcPsi.parent?.addRangeAfter(first, last, tryExpression.sourcePsi)
        tryExprSrcPsi.delete()
      }

      elementsToDelete.forEach { it.delete() }
    }
  }

  private class AssertionsConverter : AbstractUastVisitor() {
    override fun visitCallExpression(node: UCallExpression): Boolean {
      if (!node.isPsiValid) return true
      val method = node.resolveToUElementOfType<UMethod>() ?: return false
      if (!method.isStatic) return false // assert methods are always static
      val containingMethodClass = method.javaPsi.containingClass ?: return false
      val containingClassFqn = containingMethodClass.qualifiedName
      if (containingClassFqn != JUNIT_FRAMEWORK_ASSERT && containingClassFqn != JUNIT_FRAMEWORK_TEST_CASE) return false
      val project = node.sourcePsi?.project ?: return true
      val elementFactory = node.getUastElementFactory(project) ?: return false
      val newCall = elementFactory.createCallExpression(
        elementFactory.createQualifiedReference(ORG_JUNIT_ASSERT, node.sourcePsi),
        method.name, node.valueArguments, node.returnType, UastCallKind.METHOD_CALL
      ) ?: return false
      node.replace(newCall)
      return false
    }
  }

  companion object {
    private const val SETUP = "setUp"
    private const val TEARDOWN = "tearDown"
    private const val SUITE = "suite"
    private const val ADD_TEST = "addTest"
    private const val ADD_TEST_SUITE = "addTestSuite"


    private val migratableMethodNames = listOf(SETUP, TEARDOWN, SUITE, ADD_TEST, ADD_TEST_SUITE)

    private val migratableConstructorNames = listOf("TestSuite")

    private val addTestMatcher = CallMatcher.instanceCall(JUNIT_FRAMEWORK_TEST_SUITE, ADD_TEST)
      .parameterTypes(JUNIT_FRAMEWORK_TEST)

    private val addTestSuiteMatcher = CallMatcher.instanceCall(JUNIT_FRAMEWORK_TEST_SUITE, ADD_TEST_SUITE)
      .parameterTypes(JAVA_LANG_CLASS)
  }
}