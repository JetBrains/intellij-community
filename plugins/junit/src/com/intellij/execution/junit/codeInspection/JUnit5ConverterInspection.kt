// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.TestFrameworks
import com.intellij.codeInspection.*
import com.intellij.execution.JUnitBundle
import com.intellij.execution.junit.isJUnit4InScope
import com.intellij.execution.junit.isJUnit5InScope
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JavaVersionService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.siyeh.ig.junit.JUnitCommonClassNames
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.uast.UClass
import org.jetbrains.uast.sourcePsiElement

class JUnit5ConverterInspection : AbstractBaseUastLocalInspectionTool(UClass::class.java) {
  private fun shouldInspect(file: PsiFile): Boolean =
    JavaVersionService.getInstance().isAtLeast(file, JavaSdkVersion.JDK_1_8)
    && isJUnit4InScope(file) && isJUnit5InScope(file)

  override fun checkClass(aClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
    val javaPsi = aClass.javaPsi
    val framework = TestFrameworks.detectFramework(javaPsi)
    if (framework == null || "JUnit4" != framework.name) return emptyArray()
    if (!canBeConvertedToJUnit5(javaPsi)) return emptyArray()
    val identifier = aClass.uastAnchor.sourcePsiElement ?: return emptyArray()
    return arrayOf(manager.createProblemDescriptor(
      identifier, JUnitBundle.message("jvm.inspections.junit5.converter.problem.descriptor"),
      isOnTheFly, arrayOf(JUnit5ConverterQuickFix()), ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    ))
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (!shouldInspect(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return super.buildVisitor(holder, isOnTheFly)
  }
}

internal fun canBeConvertedToJUnit5(aClass: PsiClass): Boolean {
  if (AnnotationUtil.isAnnotated(aClass, TestUtils.RUN_WITH, AnnotationUtil.CHECK_HIERARCHY)) return false
  for (field in aClass.allFields) {
    if (AnnotationUtil.isAnnotated(field!!, ruleAnnotations, 0)) return false
  }
  for (method in aClass.methods) {
    if (AnnotationUtil.isAnnotated(method, ruleAnnotations, 0)) return false
    val testAnnotation = AnnotationUtil.findAnnotation(method, true, JUnitCommonClassNames.ORG_JUNIT_TEST)
    if (testAnnotation != null && testAnnotation.parameterList.attributes.isNotEmpty()) return false
  }
  return true
}

private val ruleAnnotations = listOf(JUnitCommonClassNames.ORG_JUNIT_RULE, JUnitCommonClassNames.ORG_JUNIT_CLASS_RULE)