// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.codeInsight.TestFrameworks
import com.intellij.codeInspection.*
import com.intellij.execution.JUnitBundle
import com.intellij.execution.junit.codeInspection.HamcrestCommonClassNames.ORG_HAMCREST_MATCHER_ASSERT
import com.intellij.execution.junit.isJUnit4InScope
import com.intellij.execution.junit.isJUnit5InScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JavaVersionService
import com.intellij.psi.*
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS
import com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSUMPTIONS
import com.siyeh.ig.testFrameworks.AbstractAssertHint
import com.siyeh.ig.testFrameworks.UAssertHint
import org.jetbrains.annotations.NonNls
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class JUnit5AssertionsConverterInspection(val frameworkName: @NonNls String = "JUnit5") : AbstractBaseUastLocalInspectionTool() {
  private fun shouldInspect(file: PsiFile): Boolean =
    JavaVersionService.getInstance().isAtLeast(file, JavaSdkVersion.JDK_1_8)
    && isJUnit4InScope(file) && isJUnit5InScope(file)
    && (file as? PsiClassOwner)?.classes?.all {  TestFrameworks.detectFramework(it)?.name != frameworkName } == false

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!shouldInspect(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      JUnit5AssertionsConverterVisitor(holder),
      arrayOf(UCallExpression::class.java, UCallableReferenceExpression::class.java),
      true
    )
  }

  private fun getNewAssertClassName(methodName: @NonNls String): String = when {
    methodName == "assertThat" -> ORG_HAMCREST_MATCHER_ASSERT
    methodName.startsWith("assume") -> ORG_JUNIT_JUPITER_API_ASSUMPTIONS
    else -> ORG_JUNIT_JUPITER_API_ASSERTIONS
  }

  inner class JUnit5AssertionsConverterVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
    override fun visitCallExpression(node: UCallExpression): Boolean {
      if (node.methodIdentifier == null) return true
      doCheck(node, { node.methodIdentifier?.sourcePsi }) {
        UAssertHint.create(node) { AbstractAssertHint.ASSERT_METHOD_2_PARAMETER_COUNT[it] }
      }
      return true
    }

    override fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean {
      doCheck(node, { node.sourcePsi }) {
        UAssertHint.create(node) { AbstractAssertHint.ASSERT_METHOD_2_PARAMETER_COUNT[it] }
      }
      return true
    }

    private fun doCheck(node: UExpression, toHighlight: () -> PsiElement?, createHint: () -> AbstractAssertHint<UExpression>?) {
      val sourcePsi = node.sourcePsi ?: return
      val hint = createHint() ?: return
      val psiMethod = hint.method
      if (!psiMethod.hasModifierProperty(PsiModifier.STATIC)) return
      if (!hint.isMessageOnFirstPosition) return
      val file = sourcePsi.containingFile as PsiClassOwner
      for (psiClass in file.classes) {
        val testFramework = TestFrameworks.detectFramework(psiClass) ?: continue
        if (frameworkName == testFramework.name) {
          val highlight = toHighlight() ?: return
          registerError(psiMethod, highlight)
          break
        }
      }
    }

    private fun registerError(psiMethod: PsiMethod, toHighlight: PsiElement) {
      val containingClass = psiMethod.containingClass ?: return
      val methodName = psiMethod.name
      val assertClassName = getNewAssertClassName(methodName)
      val message = JUnitBundle.message("jvm.inspections.junit5.assertions.converter.problem.descriptor", containingClass.name, assertClassName)
      if (!absentInJUnit5(psiMethod, methodName)) holder.registerProblem(
        toHighlight, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, ReplaceObsoleteAssertsFix(getNewAssertClassName(methodName))
      )
      else holder.registerProblem(toHighlight, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
    }

    private fun absentInJUnit5(psiMethod: PsiMethod, methodName: @NonNls String): Boolean {
      if ("assertNotEquals" == methodName) {
        val parameters = psiMethod.parameterList.parameters
        if (parameters.isNotEmpty()) {
          val lastParamIdx = if (parameters.first().type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) 3 else 2
          if (parameters.size > lastParamIdx && parameters[lastParamIdx].type is PsiPrimitiveType) return true
        }
      }
      return false
    }
  }

  inner class ReplaceObsoleteAssertsFix(private val baseClassName: String) : LocalQuickFix {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val element = descriptor.psiElement
      when (val uElement = element.toUElement()) {
        is UCallableReferenceExpression -> {
          val methodName = uElement.callableName
          val newClassName = JavaPsiFacade.getInstance(project).findClass(
            getNewAssertClassName(methodName), element.resolveScope
          )?.qualifiedName ?: return
          val psiFactory = uElement.getUastElementFactory(project) ?: return
          val newQualifier = psiFactory.createQualifiedReference(newClassName, element) ?: return
          val newCallableReferences = psiFactory.createCallableReferenceExpression(newQualifier, methodName, null) ?: return
          uElement.replace(newCallableReferences) ?: return
        }
        is UIdentifier -> { // UCallExpression
          val methodCall = uElement.getParentOfType<UCallExpression>() ?: return
          val methodName = methodCall.methodName ?: return
          val assertHint = UAssertHint.create(methodCall) {
            AbstractAssertHint.ASSERT_METHOD_2_PARAMETER_COUNT[it]
          } ?: return
          val arguments = methodCall.valueArguments.toMutableList()
          if ("assertThat" != methodName) {
            assertHint.message?.let {
              arguments.remove(it)
              arguments.add(it)
            }
          }
          val psiFactory = methodCall.getUastElementFactory(project) ?: return
          val clazz = JavaPsiFacade.getInstance(project).findClass(
            getNewAssertClassName(methodName), element.resolveScope
          ) ?: return
          val newClassName = clazz.qualifiedName ?: return
          val newReceiver = psiFactory.createQualifiedReference(newClassName, methodCall.sourcePsi)
          val newCall = psiFactory.createCallExpression(
            newReceiver, methodName, arguments, null, methodCall.kind, null
          ) ?: return
          val qualifiedCall = methodCall.getQualifiedParentOrThis()
          qualifiedCall.replace(newCall)
        }
      }
    }

    override fun getName(): String = JUnitBundle.message("jvm.inspections.junit5.assertions.converter.quickfix", baseClassName)

    override fun getFamilyName(): String = JUnitBundle.message("jvm.inspections.junit5.assertions.converter.familyName")
  }
}