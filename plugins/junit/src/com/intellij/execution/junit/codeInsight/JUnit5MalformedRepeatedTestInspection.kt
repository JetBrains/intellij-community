// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit.codeInsight

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.MetaAnnotationUtil
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.execution.junit.JUnitUtil
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JavaVersionService
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.ig.junit.JUnitCommonClassNames
import com.siyeh.ig.psiutils.ExpressionUtils
import org.jetbrains.annotations.Nls

class JUnit5MalformedRepeatedTestInspection : AbstractBaseJavaLocalInspectionTool() {
  object Annotations {
    val NON_REPEATED_ANNOTATIONS = listOf(JUnitUtil.TEST5_ANNOTATION,
                                          JUnitUtil.TEST5_FACTORY_ANNOTATION,
                                          JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST)
  }

  @Nls
  override fun getDisplayName(): String {
    return InspectionGadgetsBundle.message("junit5.malformed.repeated.test.display.name")
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val file = holder.file
    if (!JavaVersionService.getInstance().isAtLeast(file, JavaSdkVersion.JDK_1_8)) return PsiElementVisitor.EMPTY_VISITOR
    if (JavaPsiFacade.getInstance(file.project).
      findClass(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_REPEATED_TEST, file.resolveScope) == null) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return object : JavaElementVisitor() {

      override fun visitMethod(method: PsiMethod) {
        val modifierList = method.modifierList
        val repeatedAnno = modifierList.findAnnotation(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_REPEATED_TEST)
        if (repeatedAnno != null) {
          val testAnno = AnnotationUtil.findAnnotations(method, JUnitUtil.TEST5_JUPITER_ANNOTATIONS)
          if (testAnno.isNotEmpty()) {
            holder.registerProblem(testAnno[0], "Suspicious combination @Test and @RepeatedTest",
                                   DeleteElementFix(testAnno[0]))
          }
          val repeatedNumber = repeatedAnno.findDeclaredAttributeValue("value")
          if (repeatedNumber is PsiExpression) {
            val constant = ExpressionUtils.computeConstantExpression(repeatedNumber)
            if (constant is Int && constant <= 0) {
              holder.registerProblem(repeatedNumber, "The number of repetitions must be greater than zero")
            }
          }
        }
        else {
          val repetitionInfo = JavaPsiFacade.getInstance(holder.project).findClass(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_REPETITION_INFO, file.resolveScope)
          val repetitionType = JavaPsiFacade.getElementFactory(holder.project).createType(repetitionInfo!!)
          val repetitionInfoParam = method.parameterList.parameters.find { it.type == repetitionType }
          if (repetitionInfoParam != null) {
            if (MetaAnnotationUtil.isMetaAnnotated(method, Annotations.NON_REPEATED_ANNOTATIONS)) {
              holder.registerProblem(repetitionInfoParam.nameIdentifier ?: repetitionInfoParam, "RepetitionInfo is injected for @RepeatedTest only")
            }
            else {
              val anno = MetaAnnotationUtil.findMetaAnnotations(method, JUnitUtil.TEST5_STATIC_CONFIG_METHODS).findFirst().orElse(null)
              if (anno != null) {
                val qName = anno.qualifiedName
                holder.registerProblem(repetitionInfoParam.nameIdentifier ?: repetitionInfoParam,
                                       "RepetitionInfo is injected for @BeforeEach/@AfterEach only, but not for " + StringUtil.getShortName(qName!!))
              }
              else {
                if (MetaAnnotationUtil.isMetaAnnotated(method, JUnitUtil.TEST5_CONFIG_METHODS) && method.containingClass?.methods?.find { MetaAnnotationUtil.isMetaAnnotated(it, Annotations.NON_REPEATED_ANNOTATIONS)} != null) {
                  holder.registerProblem(repetitionInfoParam.nameIdentifier ?: repetitionInfoParam,
                                         "RepetitionInfo won't be injected for @Test methods")
                }
              }
            }
          }
        }
      }
    }
  }
}