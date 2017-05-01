/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.junit.codeInsight

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.execution.junit.codeInsight.references.MethodSourceReference
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JavaVersionService
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.util.containers.ContainerUtil
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.annotations.Nls

class JUnit5MalformedParameterizedInspection : BaseJavaBatchLocalInspectionTool() {

  @Nls
  override fun getDisplayName(): String {
    return InspectionGadgetsBundle.message("junit5.valid.parameterized.configuration.display.name")
  }


  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val file = holder.file
    if (!JavaVersionService.getInstance().isAtLeast(file, JavaSdkVersion.JDK_1_8)) return PsiElementVisitor.EMPTY_VISITOR
    if (JavaPsiFacade.getInstance(file.project).findClass(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST, file.resolveScope) == null) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return object : JavaElementVisitor() {

      override fun visitMethod(method: PsiMethod) {
        val modifierList = method.modifierList
        val parameterizedAnnotation = modifierList.findAnnotation(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST)
        if (parameterizedAnnotation != null) {
          val testAnnotation = modifierList.findAnnotation(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST)
          if (testAnnotation != null && method.parameterList.parametersCount > 0) {
            holder.registerProblem(testAnnotation,
                                   "Suspicious combination @Test and @ParameterizedTest",
                                   DeleteElementFix(testAnnotation))
          }
          val methodSource = modifierList.findAnnotation(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE)
          if (methodSource != null) {
            checkMethodSource(method, methodSource)
          }

          val valuesSource = modifierList.findAnnotation(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_VALUES_SOURCE)
          if (valuesSource != null) {
            checkValuesSource(method, valuesSource)
          }

          val enumSource = modifierList.findAnnotation(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_ENUM_SOURCE)
          if (enumSource != null) {
            checkEnumSource(method, enumSource)
          }

          val csvFileSource = modifierList.findAnnotation(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE)
          val csvSource     = modifierList.findAnnotation(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_SOURCE)
          val argSources = modifierList.findAnnotation(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS_SOURCE)

          val noMultiArgsProvider = methodSource == null && csvFileSource == null && csvSource == null && argSources == null

          if (valuesSource == null && enumSource == null && noMultiArgsProvider) {
            holder.registerProblem(parameterizedAnnotation, "No sources are provided, the suite would be empty")
          }
          else if (method.parameterList.parametersCount > 1 && noMultiArgsProvider) {
            holder.registerProblem(valuesSource ?: enumSource!!, "Multiple parameters are not supported by this source")
          }
        }
      }

      private fun checkEnumSource(method: PsiMethod, enumSource: PsiAnnotation) {
        processArrayInAnnotationParameter(enumSource.findAttributeValue("value"), { value ->
          if (value is PsiClassObjectAccessExpression) {
            checkSourceTypeAndParameterTypeAgree(method, value, value.operand.type)
          }
        })
      }

      private fun checkValuesSource(method: PsiMethod, valuesSource: PsiAnnotation) {
        val possibleValues = ContainerUtil.immutableMapBuilder<String, PsiType>()
          .put("strings", PsiType.getJavaLangString(method.manager, method.resolveScope))
          .put("ints", PsiType.INT)
          .put("longs", PsiType.LONG)
          .put("doubles", PsiType.DOUBLE).build()

        for (valueKey in possibleValues.keys) {
          processArrayInAnnotationParameter(valuesSource.findDeclaredAttributeValue(valueKey),
                                            { value -> checkSourceTypeAndParameterTypeAgree(method, value, possibleValues[valueKey]!!) })
        }

        val attributesNumber = valuesSource.parameterList.attributes.size
        if (attributesNumber > 1) {
          holder.registerProblem(valuesSource, "Exactly one type of input must be provided")
        }
        else if (attributesNumber == 0) {
          holder.registerProblem(valuesSource, "No value source is defined")
        }
      }

      private fun checkMethodSource(method: PsiMethod, methodSource: PsiAnnotation) {
        val annotationMemberValue = methodSource.findDeclaredAttributeValue("names")
        processArrayInAnnotationParameter(annotationMemberValue, { attributeValue ->
          for (reference in attributeValue.references) {
            if (reference is MethodSourceReference) {
              val resolve = reference.resolve()
              if (resolve !is PsiMethod) {
                holder.registerProblem(attributeValue,
                                       "Cannot resolve target method source: \'" + reference.value + "\'")
              }
              else {
                val sourceProvider : PsiMethod = resolve
                val providerName = sourceProvider.name

                if (!sourceProvider.hasModifierProperty(PsiModifier.STATIC)) {
                  holder.registerProblem(attributeValue, "Method source \'$providerName\' must be static",
                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                         QuickFixFactory.getInstance().createModifierListFix(sourceProvider, PsiModifier.STATIC, true, false))
                }
                else if (sourceProvider.parameterList.parametersCount != 0) {
                  holder.registerProblem(attributeValue, "Method source \'$providerName\' should have no parameters")
                }
                else {
                  val componentType = getComponentType(sourceProvider.returnType, method)
                  if (componentType == null) {
                    holder.registerProblem(attributeValue,
                                           "Method source \'$providerName\' must have one of the following return type: Stream<?>, Iterator<?>, Iterable<?> or Object[]")
                  }
                  else if (method.parameterList.parametersCount > 1 && !isArgumentsInheritor(componentType)) {
                    holder.registerProblem(attributeValue, "Multiple parameters have to be wrapped in Arguments")
                  }
                }
              }
            }
          }
        })
      }

      private fun processArrayInAnnotationParameter(attributeValue: PsiAnnotationMemberValue?,
                                                    checker: (value : PsiAnnotationMemberValue) -> Unit) {
        if (attributeValue is PsiLiteral || attributeValue is PsiClassObjectAccessExpression) {
          checker.invoke(attributeValue)
        }
        else if (attributeValue is PsiArrayInitializerMemberValue) {
          for (memberValue in attributeValue.initializers) {
            processArrayInAnnotationParameter(memberValue, checker)
          }
        }
      }

      private fun checkSourceTypeAndParameterTypeAgree(method: PsiMethod,
                                                       attributeValue: PsiAnnotationMemberValue,
                                                       componentType: PsiType) {
        val parameters = method.parameterList.parameters
        if (parameters.size == 1) {
          val paramType = parameters[0].type
          if (!paramType.isAssignableFrom(componentType) && !isArgumentsInheritor(componentType)) {
            if (componentType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
              //implicit conversion to primitive/wrapper
              if (TypeConversionUtil.isPrimitiveAndNotNullOrWrapper(paramType)) return
              val psiClass = PsiUtil.resolveClassInClassTypeOnly(paramType)
              //implicit conversion to enum
              if (psiClass != null) {
                if (psiClass.isEnum && psiClass.findFieldByName((attributeValue as PsiLiteral).value as String?, false) != null) return
                //implicit java time conversion
                val qualifiedName = psiClass.qualifiedName
                if (qualifiedName != null && qualifiedName.startsWith("java.time.")) return
              }
            }
            if (AnnotationUtil.isAnnotated(parameters[0], JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_CONVERTER_CONVERT_WITH, false)) return
            holder.registerProblem(attributeValue,
                                   "No implicit conversion found to convert object of type " + componentType.presentableText + " to " + paramType.presentableText)
          }
        }
      }

      private fun isArgumentsInheritor(componentType: PsiType): Boolean {
        return InheritanceUtil.isInheritor(componentType, JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS)
      }

      private fun getComponentType(returnType: PsiType?, method: PsiMethod): PsiType? {
        val collectionItemType = JavaGenericsUtil.getCollectionItemType(returnType, method.resolveScope)
        if (collectionItemType != null) {
          return collectionItemType
        }

        val streamItemType = PsiUtil.substituteTypeParameter(returnType, CommonClassNames.JAVA_UTIL_STREAM_STREAM, 0, false)
        if (streamItemType != null) {
          return streamItemType
        }

        return PsiUtil.substituteTypeParameter(returnType, CommonClassNames.JAVA_UTIL_ITERATOR, 0, false)
      }
    }
  }
}
