// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit.codeInsight

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.MetaAnnotationUtil
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil
import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodQuickFix
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix
import com.intellij.codeInsight.daemon.quickFix.FileReferenceQuickFixProvider
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.execution.junit.JUnitUtil
import com.intellij.execution.junit.codeInsight.references.MethodSourceReference
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JavaVersionService
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.TypeConversionUtil
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.ig.junit.JUnitCommonClassNames
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.annotations.Nls
import java.util.*

class JUnit5MalformedParameterizedInspection : AbstractBaseJavaLocalInspectionTool() {
  private object Annotations {
    val EXTENDS_WITH = listOf(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH)
  }

  @Nls
  override fun getDisplayName(): String = InspectionGadgetsBundle.message("junit5.valid.parameterized.configuration.display.name")


  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val file = holder.file
    if (!JavaVersionService.getInstance().isAtLeast(file, JavaSdkVersion.JDK_1_8)) return PsiElementVisitor.EMPTY_VISITOR
    if (JavaPsiFacade.getInstance(file.project).findClass(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST, file.resolveScope) == null) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return object : JavaElementVisitor() {

      override fun visitMethod(method: PsiMethod) {
        val parameterizedAnnotation = AnnotationUtil.findAnnotations(method, Collections.singletonList(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST))
        val testAnnotation = AnnotationUtil.findAnnotations(method, JUnitUtil.TEST5_JUPITER_ANNOTATIONS)
        if (parameterizedAnnotation.isNotEmpty()) {
          if (testAnnotation.isNotEmpty() && method.parameterList.parametersCount > 0) {
            holder.registerProblem(testAnnotation[0],
                                   "Suspicious combination @Test and @ParameterizedTest",
                                   DeleteElementFix(testAnnotation[0]))
          }

          var noMultiArgsProvider = true
          var source : PsiAnnotation? = null
          MetaAnnotationUtil.findMetaAnnotations(method, JUnitCommonClassNames.SOURCE_ANNOTATIONS).forEach {
            when (it.qualifiedName) {
              JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE -> {
                checkMethodSource(method, it)
                noMultiArgsProvider = false
              }
              JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_VALUES_SOURCE -> {
                checkValuesSource(method, it)
                source = it
              }
              JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_ENUM_SOURCE -> {
                checkEnumSource(method, it)
                source = it
              }
              JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE -> {
                checkFileSource(it)
                noMultiArgsProvider = false
              }
              JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_SOURCE -> {
                noMultiArgsProvider = false
              }
              JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS_SOURCE -> {
                if (source == null) {
                  noMultiArgsProvider = false
                }
              }
            }
          }

          if (noMultiArgsProvider) {
            if (source == null) {
              holder.registerProblem(parameterizedAnnotation[0], "No sources are provided, the suite would be empty")
            }
            else if (hasMultipleParameters(method)) {
              holder.registerProblem(source!!, "Multiple parameters are not supported by this source")
            }
          }
        }
        else if (testAnnotation.isNotEmpty() && MetaAnnotationUtil.isMetaAnnotated(method, JUnitCommonClassNames.SOURCE_ANNOTATIONS)) {
          holder.registerProblem(testAnnotation[0],
                                 "Suspicious combination @Test and parameterized source",
                                 ChangeAnnotationFix(testAnnotation[0], JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST))
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
        val possibleValues = mapOf(
          "strings" to PsiType.getJavaLangString(method.manager, method.resolveScope),
          "ints" to PsiType.INT,
          "longs" to PsiType.LONG,
          "doubles" to PsiType.DOUBLE,
          "shorts" to PsiType.SHORT,
          "bytes" to PsiType.BYTE,
          "floats" to PsiType.FLOAT,
          "chars" to PsiType.CHAR,
          "classes" to PsiType.getJavaLangClass(method.manager, method.resolveScope))

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

      private fun checkFileSource(methodSource: PsiAnnotation) {
        val annotationMemberValue = methodSource.findDeclaredAttributeValue("resources")
        processArrayInAnnotationParameter(annotationMemberValue, { attributeValue ->
          val refs = attributeValue.references.filter { it -> it is FileReference }
          if (refs.find { reference -> reference.resolve() != null } == null) {
            val reference = refs.first()
            val fixes = if (reference != null) FileReferenceQuickFixProvider.registerQuickFix(reference as FileReference).toTypedArray()
                        else emptyArray()
            holder.registerProblem(attributeValue, "Cannot resolve file source: \'${attributeValue.text}\'", *fixes)
          }
        })
      }

      private fun checkMethodSource(method: PsiMethod, methodSource: PsiAnnotation) {
        val containingClass = method.containingClass!!
        val annotationMemberValue = methodSource.findDeclaredAttributeValue("value")
        if (annotationMemberValue == null) {
          if (methodSource.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME) == null) return
          val providerName = method.name
          val methods = containingClass.findMethodsBySignature(
            JavaPsiFacade.getElementFactory(method.project).createMethodFromText("void $providerName()", method), false)
          if (!methods.isEmpty()) {
            doCheckSourceProvider(methods[0], containingClass, methodSource, method)
          }
          else {
            highlightAbsentSourceProvider(containingClass, methodSource, providerName)
          }
        }
        else {
          processArrayInAnnotationParameter(annotationMemberValue, { attributeValue ->
            for (reference in attributeValue.references) {
              if (reference is MethodSourceReference) {
                val resolve = reference.resolve()
                if (resolve !is PsiMethod) {
                  highlightAbsentSourceProvider(containingClass, attributeValue, reference.value)
                }
                else {
                  val sourceProvider : PsiMethod = resolve
                  doCheckSourceProvider(sourceProvider, containingClass, attributeValue, method)
                }
              }
            }
          })
        }
      }

      private fun highlightAbsentSourceProvider(containingClass: PsiClass,
                                                attributeValue: PsiElement,
                                                sourceProviderName: String) {
        var createFix: CreateMethodQuickFix? = null
        if (holder.isOnTheFly) {
          val staticModifier = if (!TestUtils.testInstancePerClass(containingClass)) " static" else "";
          createFix = CreateMethodQuickFix.createFix(containingClass,
                                                     "private$staticModifier Object[][] $sourceProviderName()",
                                                     "return new Object[][] {};")
        }
        holder.registerProblem(attributeValue,
                               "Cannot resolve target method source: \'$sourceProviderName\'",
                               createFix)
      }

      private fun doCheckSourceProvider(sourceProvider: PsiMethod,
                                        containingClass: PsiClass?,
                                        attributeValue: PsiElement,
                                        method: PsiMethod) {
        val providerName = sourceProvider.name

        if (!sourceProvider.hasModifierProperty(PsiModifier.STATIC) &&
            containingClass != null && !TestUtils.testInstancePerClass(containingClass)) {
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
          else if (hasMultipleParameters(method) && !isArgumentsInheritor(componentType) &&
                   !componentType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) &&
                   !componentType.deepComponentType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
            holder.registerProblem(attributeValue, "Multiple parameters have to be wrapped in Arguments")
          }
        }
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
                if (qualifiedName != null) {
                  if (qualifiedName.startsWith("java.time.")) return
                  if (qualifiedName.equals("java.nio.file.Path")) return
                }

                val factoryMethod: (PsiMethod) -> Boolean = {
                  !it.hasModifier(JvmModifier.PRIVATE) &&
                   it.parameterList.parametersCount == 1 &&
                   it.parameterList.parameters[0].type.equalsToText(CommonClassNames.JAVA_LANG_STRING)
                }

                if (!psiClass.hasModifier(JvmModifier.ABSTRACT) && psiClass.constructors.find(factoryMethod) != null) return
                if (psiClass.methods.find { it.hasModifier(JvmModifier.STATIC) && factoryMethod(it) } != null) return
              }
            }
            if (AnnotationUtil.isAnnotated(parameters[0], JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_CONVERTER_CONVERT_WITH, 0)) return
            holder.registerProblem(attributeValue,
                                   "No implicit conversion found to convert object of type ${componentType.presentableText} to ${paramType.presentableText}")
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

        if (InheritanceUtil.isInheritor(returnType, CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM)) return PsiType.INT
        if (InheritanceUtil.isInheritor(returnType, CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM)) return PsiType.LONG
        if (InheritanceUtil.isInheritor(returnType, CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM)) return PsiType.DOUBLE

        val streamItemType = PsiUtil.substituteTypeParameter(returnType, CommonClassNames.JAVA_UTIL_STREAM_STREAM, 0, true)
        if (streamItemType != null) {
          return streamItemType
        }

        return PsiUtil.substituteTypeParameter(returnType, CommonClassNames.JAVA_UTIL_ITERATOR, 0, true)
      }
    }
  }

  private fun hasMultipleParameters(method: PsiMethod): Boolean {
    val containingClass = method.containingClass
    return containingClass != null &&
             method.parameterList.parameters
             .filter {
               !InheritanceUtil.isInheritor(it.type, JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_INFO) &&
               !InheritanceUtil.isInheritor(it.type, JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_REPORTER)
             }
             .count() > 1
           && !MetaAnnotationUtil.isMetaAnnotated(method, Annotations.EXTENDS_WITH)
           && !MetaAnnotationUtil.isMetaAnnotatedInHierarchy(containingClass, Annotations.EXTENDS_WITH)
  }
}


class ChangeAnnotationFix(testAnnotation: PsiAnnotation, val targetAnnotation: String) : LocalQuickFixAndIntentionActionOnPsiElement(testAnnotation) {
  override fun getFamilyName(): String = "Replace annotation"

  override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
    val annotation = JavaPsiFacade.getElementFactory(project).createAnnotationFromText("@" + targetAnnotation, startElement)
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(startElement.replace(annotation))
  }

  override fun getText(): String = "Change to " + StringUtil.getShortName(targetAnnotation)

}
