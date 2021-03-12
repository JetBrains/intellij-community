// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit.codeInsight

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.intention.AddAnnotationFix
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.execution.JUnitBundle
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.PsiClassImplUtil
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil
import com.siyeh.ig.junit.JUnitCommonClassNames

class JUnit5MalformedOrderedTestInspection : AbstractBaseJavaLocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val file = holder.file
    if (JavaPsiFacade.getInstance(file.project).findClass(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST, file.resolveScope) == null) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    val psiClassOrderAnnotationOrderer = JavaPsiFacade.getInstance(file.project)
                                           .findClass(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_METHOD_ORDERER_ORDER_ANNOTATION,
                                                      file.resolveScope)
                                         ?: return PsiElementVisitor.EMPTY_VISITOR

    return object : JavaElementVisitor() {
      override fun visitClass(aClass: PsiClass?) {
        if (aClass == null || aClass.hasModifier(JvmModifier.ABSTRACT) || aClass.isInterface) {
          return
        }

        val hasTestableMethodsWithOrder = aClass.allMethods.any {
          isTestableMethodWithOrderAnnotation(it)
        }

        if (!hasTestableMethodsWithOrder) {
          return
        }

        val testMethodOrderAnnotation = detectProblemOnPsiClassWithoutTestMethodOrderAnnotation(
          classWithTestMethodOrderer = aClass,
          problemOnPsiElement = aClass.nameIdentifier ?: return,
          problemDescription = JUnitBundle.message("junit5.malformed.parameterized.inspection.description.test.class.without.annotation", StringUtil.getShortName(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ORDER), StringUtil.getShortName(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER)))
                                        ?: return

        detectProblemWithTestMethodOrderAnnotationValue(
          testMethodOrderAnnotation = testMethodOrderAnnotation,
          placeOnPsiClass = aClass,
          problemOnPsiElement = aClass.nameIdentifier ?: return,
          problemDescription = JUnitBundle.message("junit5.malformed.parameterized.inspection.description.test.class.without.order.support", StringUtil.getShortName(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ORDER), StringUtil.getShortName(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER)))
      }

      override fun visitMethod(method: PsiMethod) {
        if (!isTestableMethodWithOrderAnnotation(method)) {
          return
        }

        val psiClass = method.containingClass ?: return
        if (psiClass.hasModifier(JvmModifier.ABSTRACT) || psiClass.isInterface) {
          return
        }

        val testMethodOrderAnnotation = detectProblemOnPsiClassWithoutTestMethodOrderAnnotation(
          classWithTestMethodOrderer = psiClass,
          problemOnPsiElement = method.nameIdentifier ?: return,
          problemDescription = JUnitBundle.message("junit5.malformed.parameterized.inspection.description.test.method.without.annotation", StringUtil.getShortName(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ORDER), StringUtil.getShortName(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER)))
                                        ?: return

        detectProblemWithTestMethodOrderAnnotationValue(
          testMethodOrderAnnotation = testMethodOrderAnnotation,
          placeOnPsiClass = psiClass,
          problemOnPsiElement = method.nameIdentifier ?: return,
          problemDescription = JUnitBundle.message("junit5.malformed.parameterized.inspection.description.test.method.without.order.support", StringUtil.getShortName(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ORDER), StringUtil.getShortName(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER)))
      }

      /**
       * @return `null` when problem has been found, otherwise no problem decided returning instance of [@PsiAnnotation]
       * on which decision could be made
       */
      private fun detectProblemOnPsiClassWithoutTestMethodOrderAnnotation(
        classWithTestMethodOrderer: PsiClass,
        problemOnPsiElement: PsiElement,
        problemDescription: String
      ): PsiAnnotation? {
        val testMethodOrderAnnotation = AnnotationUtil.findAnnotationInHierarchy(classWithTestMethodOrderer, setOf(
          JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER))
        if (testMethodOrderAnnotation == null) {
          holder.registerProblem(
            problemOnPsiElement,
            problemDescription,
            object : AddAnnotationFix(
              JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER,
              classWithTestMethodOrderer,
              createAnnotationValues(classWithTestMethodOrderer)) {
              override fun getText(): String = JUnitBundle.message("junit5.malformed.parameterized.fix.ordered.add.text", StringUtil.getShortName(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER), StringUtil.getShortName(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ORDER))

              override fun getFamilyName(): String = JUnitBundle.message("junit5.malformed.parameterized.fix.ordered.family.name", StringUtil.getShortName(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER))
            })
          return null
        }
        else {
          return testMethodOrderAnnotation
        }
      }

      private fun detectProblemWithTestMethodOrderAnnotationValue(
        testMethodOrderAnnotation: PsiAnnotation,
        placeOnPsiClass: PsiClass,
        problemOnPsiElement: PsiElement,
        problemDescription: String
      ) {
        val value = testMethodOrderAnnotation.findAttributeValue("value")
                      as PsiClassObjectAccessExpression? ?: return

        val valuePsiClass = PsiTypesUtil.getPsiClass(value.operand.type) ?: return
        if (PsiClassImplUtil.isClassEquivalentTo(valuePsiClass, psiClassOrderAnnotationOrderer)) {
          // annotation value is known correct MethodOrderer implementation
          return
        }

        val packageName = PsiUtil.getPackageName(valuePsiClass) ?: return
        if (packageName == StringUtil.getPackageName(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ORDER)) {
          // other JUnit5's MethodOrderer implementations ignore @Order annotation
          holder.registerProblem(
            problemOnPsiElement,
            problemDescription,
            object : AddAnnotationFix(
              JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER,
              placeOnPsiClass,
              createAnnotationValues(placeOnPsiClass),
              testMethodOrderAnnotation.qualifiedName
            ) {
              override fun getText(): String = JUnitBundle.message("junit5.malformed.parameterized.fix.ordered.replace.text", StringUtil.getShortName(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER), StringUtil.getShortName(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ORDER))

              override fun getFamilyName(): String = JUnitBundle.message("junit5.malformed.parameterized.fix.ordered.family.name", StringUtil.getShortName(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER))

              override fun isAvailable(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean {
                return (startElement as PsiModifierListOwner).let {
                  if (!it.isValid) false
                  else PsiUtil.isLanguageLevel5OrHigher(it)
                }
              }
            })
        }
        // but custom implementation of MethodOrderer may take @Order annotation into account (cannot decide)
      }

      private fun createAnnotationValues(context: PsiClass): Array<out PsiNameValuePair>? {
        val annotationFqn = JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER
        val ordererFqn = JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_METHOD_ORDERER_ORDER_ANNOTATION
        return JavaPsiFacade.getInstance(context.project)
          .elementFactory
          .createAnnotationFromText("""@$annotationFqn(value = $ordererFqn.class)""", context)
          .parameterList
          .attributes
      }
    }
  }

  private fun isTestableMethodWithOrderAnnotation(it: PsiMethod) =
    AnnotationUtil.findAnnotationInHierarchy(it, testableMethods) != null
    && AnnotationUtil.findAnnotationInHierarchy(it, setOf(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ORDER)) != null

  companion object {
    private val testableMethods = setOf(
      JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST,
      JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_REPEATED_TEST,
      JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST,
      JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_FACTORY,
      JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_TEMPLATE,
      JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_DYNAMIC_TEST
    )
  }
}

