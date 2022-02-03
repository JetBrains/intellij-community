// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInsight

import com.intellij.codeInsight.MetaAnnotationUtil
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.*
import com.intellij.execution.JUnitBundle
import com.intellij.execution.junit.JUnitUtil
import com.intellij.execution.junit.codeInsight.JUnit5MalformedParameterizedInspection.Annotations.METHOD_SOURCE_RETURN_TYPE
import com.intellij.execution.junit.codeInsight.references.MethodSourceReference
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.tree.java.PsiNameValuePairImpl
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.psi.util.isAncestor
import com.intellij.uast.UastHintedVisitorAdapter
import com.intellij.util.SmartList
import com.siyeh.ig.junit.JUnitCommonClassNames
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import java.util.*
import java.util.stream.Collectors

class JUnit5MalformedParameterizedInspection : AbstractBaseUastLocalInspectionTool() {
  internal object Annotations {
    const val TEST_INSTANCE_PER_CLASS = "@org.junit.jupiter.api.TestInstance(TestInstance.Lifecycle.PER_CLASS)"
    const val METHOD_SOURCE_RETURN_TYPE = "java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments>"
    val EXTENDS_WITH = listOf(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH)
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val file = holder.file

    if (JavaPsiFacade.getInstance(file.project).findClass(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST,
                                                          file.resolveScope) == null) {
      return PsiElementVisitor.EMPTY_VISITOR
    }

    return UastHintedVisitorAdapter.create(file.language, object : AbstractUastNonRecursiveVisitor() {
      override fun visitMethod(node: UMethod): Boolean {
        val parameterizedAnnotation = findAnnotations(node, Collections.singletonList(
          JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST))
        val testAnnotation = findAnnotations(node, JUnitUtil.TEST5_JUPITER_ANNOTATIONS.toMutableList())
        if (parameterizedAnnotation.isNotEmpty()) {
          if (testAnnotation.isNotEmpty() && node.uastParameters.isNotEmpty() && testAnnotation[0].sourcePsi != null) {
            testAnnotation[0].uastAnchor?.sourcePsi?.let {
              holder.registerProblem(it,
                                     JUnitBundle.message(
                                       "junit5.malformed.parameterized.inspection.description.suspicious.combination.test.and.parameterizedtest"),
                                     DeleteElementFix(testAnnotation[0].sourcePsi!!))
            }
          }

          val singleParameterProviderChecker = SingleParameterChecker(holder)
          val methodSourceChecker = MethodSourceChecker(holder)
          val csvChecker = CsvChecker(holder)
          val nullOrEmptySourceChecker = NullOrEmptySourceChecker(holder)
          val usedSourceAnnotations = MetaAnnotationUtil.findMetaAnnotations(node.javaPsi, JUnitCommonClassNames.SOURCE_ANNOTATIONS).map {
            when (it.qualifiedName) {
              JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE -> {
                methodSourceChecker.checkMethodSource(node, it)
              }
              JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_VALUES_SOURCE -> {
                singleParameterProviderChecker.checkValuesSource(node, it)
              }
              JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_ENUM_SOURCE -> {
                singleParameterProviderChecker.checkEnumSource(node, it)
              }
              JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE -> {
                csvChecker.checkFileSource(it)
              }
              JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_NULL_SOURCE -> {
                nullOrEmptySourceChecker.checkNullSource(node, it)
              }
              JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_EMPTY_SOURCE -> {
                nullOrEmptySourceChecker.checkEmptySource(node, it)
              }
              JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_NULL_AND_EMPTY_SOURCE -> {
                nullOrEmptySourceChecker.checkNullSource(node, it)
                nullOrEmptySourceChecker.checkEmptySource(node, it)
              }
            }
            it
          }
            .distinct()
            .collect(Collectors.toMap({ it }, { it.qualifiedName }))

          checkConflictingSourceAnnotations(usedSourceAnnotations, node, parameterizedAnnotation[0])
        }
        else {
          if (testAnnotation.isNotEmpty() && MetaAnnotationUtil.isMetaAnnotated(node.javaPsi, JUnitCommonClassNames.SOURCE_ANNOTATIONS)
              && testAnnotation[0].sourcePsi != null && testAnnotation[0].javaPsi != null) {
            testAnnotation[0].uastAnchor?.sourcePsi?.let {
              holder.registerProblem(it,
                                     JUnitBundle.message(
                                       "junit5.malformed.parameterized.inspection.description.suspicious.combination"),
                                     ChangeAnnotationFix(testAnnotation[0].javaPsi!!,
                                                         JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST))
            }
          }
        }
        return true
      }

      private fun checkConflictingSourceAnnotations(usedSourceAnnotations: MutableMap<PsiAnnotation, @NlsSafe String?>,
                                                    method: UMethod,
                                                    elementToHighlight: UAnnotation) {
        val singleParameterProviders = usedSourceAnnotations.containsValue(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_ENUM_SOURCE) ||
                                       usedSourceAnnotations.containsValue(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_VALUES_SOURCE) ||
                                       usedSourceAnnotations.containsValue(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_NULL_SOURCE) ||
                                       usedSourceAnnotations.containsValue(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_EMPTY_SOURCE) ||
                                       usedSourceAnnotations.containsValue(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_NULL_AND_EMPTY_SOURCE)

        val multipleParametersProvider = usedSourceAnnotations.containsValue(
          JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE) ||
                                         usedSourceAnnotations.containsValue(
                                           JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE) ||
                                         usedSourceAnnotations.containsValue(
                                           JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_SOURCE)

        if (!multipleParametersProvider && !singleParameterProviders &&
            hasCustomProvider(usedSourceAnnotations)) {
          return
        }

        val sourcePsi = elementToHighlight.uastAnchor?.sourcePsi ?: return
        if (!multipleParametersProvider) {
          if (!singleParameterProviders) {
            holder.registerProblem(sourcePsi,
                                   JUnitBundle.message(
                                     "junit5.malformed.parameterized.inspection.description.no.sources.are.provided"))
          }
          else if (hasMultipleParameters(method.javaPsi)) {
            holder.registerProblem(sourcePsi, JUnitBundle.message(
              "junit5.malformed.parameterized.inspection.description.multiple.parameters.are.not.supported.by.this.source"))
          }
        }
      }

      private fun hasCustomProvider(annotations: MutableMap<PsiAnnotation, String?>): Boolean {
        annotations.forEach { (anno, qName) ->
          when (qName) {
            JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS_SOURCE -> {
              return@hasCustomProvider true
            }
            JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS_SOURCES -> {
              val attributes = anno.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)
              if ((attributes as? PsiArrayInitializerMemberValue)?.initializers?.isNotEmpty() == true) {
                return@hasCustomProvider true
              }
            }
          }
        }
        return false
      }
    }, arrayOf(UMethod::class.java))
  }

  private fun findAnnotations(method: UMethod, annotationsList: List<String>): List<UAnnotation> {
    return annotationsList.mapNotNull { method.findAnnotation(it) }
  }
}

class ChangeAnnotationFix(testAnnotation: PsiAnnotation,
                          private val targetAnnotation: String) : LocalQuickFixAndIntentionActionOnPsiElement(testAnnotation) {
  override fun getFamilyName(): String = JUnitBundle.message(
    "junit5.malformed.parameterized.fix.family.name")

  override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
    val annotation = JavaPsiFacade.getElementFactory(project).createAnnotationFromText("@$targetAnnotation", startElement)
    annotation.toUElementOfType<UAnnotation>()?.let { anno ->
      startElement.toUElementOfType<UAnnotation>()?.replace(anno)
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(startElement.replace(annotation))
    }
  }

  override fun getText(): String = JUnitBundle.message("junit5.malformed.parameterized.fix.text",
                                                       StringUtil.getShortName(targetAnnotation))
}

private class CsvChecker(val holder: ProblemsHolder) {
  fun checkFileSource(methodSource: PsiAnnotation) {
    val annotationMemberValue = methodSource.findDeclaredAttributeValue("resources")
    processArrayInAnnotationParameter(annotationMemberValue) { attributeValue ->
      for (ref in attributeValue.references) {
        if (ref.isSoft) continue
        if (ref is FileReference && ref.multiResolve(false).isEmpty()) {
          holder.registerProblem(ref.element, ref.rangeInElement,
                                 JUnitBundle.message(
                                   "junit5.malformed.parameterized.inspection.description.file.source",
                                   attributeValue.text), *ref.quickFixes)
        }
      }
    }
  }
}

private class NullOrEmptySourceChecker(val holder: ProblemsHolder) {
  fun checkNullSource(method: UMethod, psiAnnotation: PsiAnnotation) {
    val size = method.uastParameters.size
    if (size != 1) {
      val sourcePsi = getElementToHighlight(psiAnnotation, method).toUElement()?.sourcePsi ?: return
      checkFormalParameters(size, sourcePsi, psiAnnotation.qualifiedName)
    }
  }

  private fun checkFormalParameters(size: Int, sourcePsi: PsiElement, sourceName : String?) {
    if (sourceName == null) return
    val errorMessageKey = if (size == 0)
      "junit5.malformed.parameterized.inspection.description.nullsource.cannot.provide.argument.no.params"
    else
      "junit5.malformed.parameterized.inspection.description.nullsource.cannot.provide.argument.too.many.params"
    holder.registerProblem(sourcePsi, JUnitBundle.message(errorMessageKey, StringUtil.getShortName(sourceName)))
  }

  fun checkEmptySource(method: UMethod, psiAnnotation: PsiAnnotation) {
    val sourcePsi = getElementToHighlight(psiAnnotation, method).toUElement()?.sourcePsi ?: return
    val size = method.uastParameters.size
    val shortName = psiAnnotation.qualifiedName ?: return
    if (size == 1) {
      val type = method.uastParameters[0].type
      if (type is PsiArrayType ||
          type.equalsToText(CommonClassNames.JAVA_LANG_STRING) ||
          type.equalsToText(CommonClassNames.JAVA_UTIL_LIST) ||
          type.equalsToText(CommonClassNames.JAVA_UTIL_SET) ||
          type.equalsToText(CommonClassNames.JAVA_UTIL_MAP)) {
        return
      }
      holder.registerProblem(sourcePsi, 
                             JUnitBundle.message("junit5.malformed.parameterized.inspection.description.emptysource.cannot.provide.argument",
                                                 StringUtil.getShortName(shortName),
                                                 type.presentableText))
    }
    else {
      checkFormalParameters(size, sourcePsi, shortName)
    }
  }
}

private class SingleParameterChecker(val holder: ProblemsHolder) {
  fun checkEnumSource(method: UMethod, enumSource: PsiAnnotation) {
    // @EnumSource#value type is Class<?>, not an array
    val value = enumSource.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)
    if (value is PsiClassObjectAccessExpression) {
      val enumType = value.operand.type
      checkSourceTypeAndParameterTypeAgree(method, value, enumType)
      checkEnumConstants(enumSource, enumType, method)
    }
  }

  fun checkValuesSource(method: UMethod, valuesSource: PsiAnnotation) {
    val psiMethod = method.javaPsi
    val possibleValues = mapOf(
      "strings" to PsiType.getJavaLangString(psiMethod.manager, psiMethod.resolveScope),
      "ints" to PsiType.INT,
      "longs" to PsiType.LONG,
      "doubles" to PsiType.DOUBLE,
      "shorts" to PsiType.SHORT,
      "bytes" to PsiType.BYTE,
      "floats" to PsiType.FLOAT,
      "chars" to PsiType.CHAR,
      "booleans" to PsiType.BOOLEAN,
      "classes" to PsiType.getJavaLangClass(psiMethod.manager, psiMethod.resolveScope))

    for (valueKey in possibleValues.keys) {
      processArrayInAnnotationParameter(valuesSource.findDeclaredAttributeValue(valueKey)) { value ->
        possibleValues[valueKey]?.let { checkSourceTypeAndParameterTypeAgree(method, value, it) }
      }
    }

    val attributesNumber = valuesSource.parameterList.attributes.size
    val sourcePsi = getElementToHighlight(valuesSource, method).toUElementOfType<UAnnotation>()?.uastAnchor?.sourcePsi ?: return

    if (attributesNumber > 1) {
      holder.registerProblem(sourcePsi, JUnitBundle.message(
        "junit5.malformed.parameterized.inspection.description.exactly.one.type.of.input.must.be.provided"))
    }
    else if (attributesNumber == 0) {
      holder.registerProblem(sourcePsi,
                             JUnitBundle.message(
                               "junit5.malformed.parameterized.inspection.description.no.value.source.is.defined"))
    }
  }

  private fun checkEnumConstants(enumSource: PsiAnnotation,
                                 enumType: PsiType,
                                 method: UMethod) {
    val mode = enumSource.findAttributeValue("mode")
    if (mode is PsiReferenceExpression && ("INCLUDE" == mode.referenceName || "EXCLUDE" == mode.referenceName)) {
      val allEnumConstants = (PsiUtil.resolveClassInClassTypeOnly(enumType) ?: return).fields
        .filterIsInstance<PsiEnumConstant>()
        .map { it.name }
        .toSet()
      val definedConstants = mutableSetOf<String>()
      processArrayInAnnotationParameter(enumSource.findAttributeValue("names")) { name ->
        if (name is PsiLiteralExpression) {
          val value = name.value
          if (value is String) {
            val sourcePsi = getElementToHighlight(name, method).toUElement()?.sourcePsi
                            ?: return@processArrayInAnnotationParameter
            if (!allEnumConstants.contains(value)) {
              holder.registerProblem(sourcePsi,
                                     JUnitBundle.message(
                                       "junit5.malformed.parameterized.inspection.description.unresolved.enum"))
            }
            else if (!definedConstants.add(value)) {
              holder.registerProblem(sourcePsi,
                                     JUnitBundle.message(
                                       "junit5.malformed.parameterized.inspection.description.duplicated.enum"))
            }
          }
        }
      }
    }
  }

  private fun checkSourceTypeAndParameterTypeAgree(method: UMethod,
                                                   attributeValue: PsiAnnotationMemberValue,
                                                   componentType: PsiType) {
    val parameters = method.uastParameters
    if (parameters.size == 1) {
      val paramType = parameters[0].type
      if (!paramType.isAssignableFrom(componentType) && !InheritanceUtil.isInheritor(componentType,
                                                                                     JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS)) {
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
              if (qualifiedName == "java.nio.file.Path") return
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
        else if (componentType.equalsToText("org.junit.jupiter.params.provider.NullEnum")) {
          val psiClass = PsiUtil.resolveClassInClassTypeOnly(paramType)
          if (psiClass != null && psiClass.isEnum) return
        }

        val sourcePsi = getElementToHighlight(attributeValue, method, parameters[0].sourcePsi as PsiNameIdentifierOwner)
          .toUElement()?.sourcePsi

        if (parameters[0].findAnnotation(
            JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_CONVERTER_CONVERT_WITH) != null || sourcePsi == null) return
        holder.registerProblem(sourcePsi,
                               JUnitBundle.message(
                                 "junit5.malformed.parameterized.inspection.description.method.source.assignable",
                                 componentType.presentableText, paramType.presentableText))
      }
    }
  }
}

private class MethodSourceChecker(val problemsHolder: ProblemsHolder) {
  fun checkMethodSource(method: UMethod, methodSource: PsiAnnotation) {
    val psiMethod = method.javaPsi
    val containingClass = psiMethod.containingClass ?: return
    val annotationMemberValue = methodSource.findDeclaredAttributeValue("value")
    if (annotationMemberValue == null) {
      if (methodSource.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME) == null) return
      val foundMethod = containingClass.findMethodsByName(method.name, true).singleOrNull { it.parameters.isEmpty() }
      val uFoundMethod = foundMethod.toUElementOfType<UMethod>()
      if (uFoundMethod != null) {
        doCheckSourceProvider(uFoundMethod, containingClass, methodSource, method)
      }
      else {
        highlightAbsentSourceProvider(containingClass, methodSource, method.name, method)
      }
    }
    else {
      processArrayInAnnotationParameter(annotationMemberValue) { attributeValue ->
        for (reference in attributeValue.references) {
          if (reference is MethodSourceReference) {
            val resolve = reference.resolve()
            if (resolve !is PsiMethod) {
              highlightAbsentSourceProvider(containingClass, attributeValue, reference.value, method)
            }
            else {
              val sourceProvider: PsiMethod = resolve
              val uSourceProvider = sourceProvider.toUElementOfType<UMethod>()
              if (uSourceProvider != null) {
                doCheckSourceProvider(uSourceProvider, containingClass, attributeValue, method)
              }
            }
          }
        }
      }
    }
  }

  private fun implementationsTestInstanceAnnotated(containingClass: PsiClass): Boolean {
    val implementations = ClassInheritorsSearch.search(containingClass, containingClass.resolveScope, true).firstOrNull {
      TestUtils.testInstancePerClass(it)
    }
    return implementations != null
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

  private fun doCheckSourceProvider(sourceProvider: UMethod,
                                    containingClass: PsiClass?,
                                    attributeValue: PsiElement,
                                    method: UMethod) {

    val sourcePsi = getElementToHighlight(attributeValue, method).toUElement()?.sourcePsi ?: return
    val providerName = sourceProvider.name
    if (!sourceProvider.isStatic &&
        containingClass != null && !TestUtils.testInstancePerClass(containingClass) &&
        !implementationsTestInstanceAnnotated(containingClass)) {
      val annotation = JavaPsiFacade.getElementFactory(containingClass.project).createAnnotationFromText(
        JUnit5MalformedParameterizedInspection.Annotations.TEST_INSTANCE_PER_CLASS, containingClass)
      val actions = SmartList<IntentionAction>()
      val value = (annotation.attributes[0] as PsiNameValuePairImpl).value
      if (value != null) {
        actions.addAll(
          createAddAnnotationActions(containingClass, annotationRequest(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_INSTANCE,
                                                                        constantAttribute("value", value.text))))
      }
      actions.addAll(sourceProvider.createMakeStaticActions())
      val intention = IntentionWrapper.wrapToQuickFixes(actions, sourceProvider.javaPsi.containingFile).toTypedArray()
      problemsHolder.registerProblem(sourcePsi,
                                     JUnitBundle.message(
                                       "junit5.malformed.parameterized.inspection.description.method.source.static",
                                       providerName),
                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                     *intention)
    }
    else if (sourceProvider.uastParameters.isNotEmpty()) {
      problemsHolder.registerProblem(sourcePsi,
                                     JUnitBundle.message(
                                       "junit5.malformed.parameterized.inspection.description.method.source.no.params",
                                       providerName))
    }
    else {
      val componentType = getComponentType(sourceProvider.returnType, method.javaPsi)
      if (componentType == null) {
        problemsHolder.registerProblem(sourcePsi,
                                       JUnitBundle.message(
                                         "junit5.malformed.parameterized.inspection.description.method.source.return.type",
                                         providerName))
      }
      else if (hasMultipleParameters(
          method.javaPsi) && !InheritanceUtil.isInheritor(componentType,
                                                          JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS) &&
               !componentType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) &&
               !componentType.deepComponentType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
        problemsHolder.registerProblem(sourcePsi,
                                       JUnitBundle.message(
                                         "junit5.malformed.parameterized.inspection.description.wrapped.in.arguments"))
      }
    }

  }

  private fun highlightAbsentSourceProvider(containingClass: PsiClass,
                                            attributeValue: PsiElement,
                                            sourceProviderName: String,
                                            method: UMethod) {
    val elementToHighlight = getElementToHighlight(attributeValue, method).toUElementOfType<UElement>() ?: return
    val sourcePsi = elementToHighlight.sourcePsi ?: return
    if (problemsHolder.isOnTheFly) {
      val modifiers = SmartList(JvmModifier.PUBLIC)
      if (!TestUtils.testInstancePerClass(containingClass)) {
        modifiers.add(JvmModifier.STATIC)
      }
      val typeFromText = JavaPsiFacade.getElementFactory(containingClass.project).createTypeFromText(
        METHOD_SOURCE_RETURN_TYPE, containingClass)
      val request = methodRequest(containingClass.project, sourceProviderName, modifiers, typeFromText)
      val actions = createMethodActions(containingClass, request)

      val intention = IntentionWrapper.wrapToQuickFixes(actions, containingClass.containingFile).toTypedArray()
      if (intention.isNotEmpty()) {
        problemsHolder.registerProblem(
          sourcePsi,
          JUnitBundle.message("junit5.malformed.parameterized.inspection.description.method.source.unresolved",
                              sourceProviderName),
          intention[0]
        )
      }
    }
  }
}

private fun processArrayInAnnotationParameter(attributeValue: PsiAnnotationMemberValue?,
                                              checker: (value: PsiAnnotationMemberValue) -> Unit) {
  if (attributeValue is PsiLiteral || attributeValue is PsiClassObjectAccessExpression) {
    checker.invoke(attributeValue)
  }
  else if (attributeValue is PsiArrayInitializerMemberValue) {
    for (memberValue in attributeValue.initializers) {
      processArrayInAnnotationParameter(memberValue, checker)
    }
  }
}

private fun hasMultipleParameters(method: PsiMethod): Boolean {
  val containingClass = method.containingClass
  return containingClass != null &&
         method.parameterList.parameters
           .count {
             !InheritanceUtil.isInheritor(it.type, JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_INFO) &&
             !InheritanceUtil.isInheritor(it.type, JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_REPORTER)
           } > 1
         && !MetaAnnotationUtil.isMetaAnnotated(method, JUnit5MalformedParameterizedInspection.Annotations.EXTENDS_WITH)
         && !MetaAnnotationUtil.isMetaAnnotatedInHierarchy(containingClass, JUnit5MalformedParameterizedInspection.Annotations.EXTENDS_WITH)
}

private fun getElementToHighlight(attributeValue: PsiElement,
                                  method: UMethod,
                                  default: PsiNameIdentifierOwner = method.javaPsi): PsiElement {
  return if (method.javaPsi.isAncestor(attributeValue, true)) attributeValue else default.nameIdentifier ?: default
}
