// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.lang.jvm.JvmParameter
import com.intellij.lang.jvm.types.JvmType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT
import com.intellij.psi.CommonClassNames.JAVA_LANG_OVERRIDE
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariablesOrder
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.getJavaLangObject
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitNode
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_OBJECT
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.forbidInteriorReturnTypeInference
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.putAll
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


class NameGenerator(private val postfix: String = "",
                    private val context: PsiElement) {
  companion object {
    private const val nameRange = ('Z'.toByte() + 1) - 'T'.toByte()

    private fun produceTypeParameterName(index: Int, postfix: String): String {
      val indexRepresentation = (index / nameRange).run { if (this == 0) "" else this.toString() }
      return ('T'.toByte() + index % nameRange).toChar().toString() + indexRepresentation + postfix
    }
  }

  private var counter = 0

  val name: String
    get() {
      while (true) {
        val name = produceTypeParameterName(counter, postfix)
        ++counter
        val newType = PsiClassType.getTypeByName(name, context.project, context.resolveScope)
        if (newType.resolve() == null) {
          return name
        }
      }
    }

}

typealias InferenceGraphNode = InferenceVariablesOrder.InferenceGraphNode<InferenceUnitNode>

fun getInferenceVariable(session: GroovyInferenceSession, variableType: PsiType): InferenceVariable? {
  return session.getInferenceVariable(session.substituteWithInferenceVariables(variableType))
}

fun Iterable<PsiType>.flattenIntersections(): Iterable<PsiType> {
  return this.flatMap { if (it is PsiIntersectionType) PsiIntersectionType.flatten(it.conjuncts, mutableSetOf()) else listOf(it) }
}

fun GroovyPsiElementFactory.createProperTypeParameter(name: String, superType: PsiType?): PsiTypeParameter {
  val extendsTypes = when {
    superType is PsiIntersectionType -> superType.conjuncts.asList()
    superType != null -> listOf(superType)
    else -> emptyList()
  }
  val filteredSupertypes = extendsTypes.filter { !it.equalsToText(JAVA_LANG_OBJECT) && !it.equalsToText(GROOVY_OBJECT) }

  val extendsBound =
    if (filteredSupertypes.isNotEmpty()) {
      " extends ${filteredSupertypes.joinToString("&") { it.getCanonicalText(false) }}"
    }
    else {
      ""
    }
  val method = "public <$name $extendsBound> void foo(){}"
  return createMethodFromText(method, null).typeParameters.single()
}

fun PsiType.forceWildcardsAsTypeArguments(): PsiType {
  val manager = resolve()?.manager ?: return this
  val factory = GroovyPsiElementFactory.getInstance(manager.project)
  return accept(object : PsiTypeMapper() {
    override fun visitClassType(classType: PsiClassType): PsiType {
      val mappedParameters = classType.parameters.map {
        val accepted = it.accept(this)
        when {
          accepted is PsiWildcardType -> accepted
          accepted != null && accepted != PsiType.NULL -> PsiWildcardType.createExtends(manager, accepted)
          else -> PsiWildcardType.createUnbounded(manager)
        }
      }
      val resolvedClass = classType.resolve()
      if (resolvedClass != null) {
        return factory.createType(resolvedClass, *mappedParameters.toTypedArray())
      }
      else {
        return classType
      }
    }

  })
}

fun PsiType?.isClosureTypeDeep(): Boolean {
  return (this as? PsiClassType)?.rawType()?.equalsToText(GROOVY_LANG_CLOSURE) ?: false
         || this?.typeParameter()?.extendsListTypes?.singleOrNull()?.rawType()?.equalsToText(GROOVY_LANG_CLOSURE) ?: false
}


tailrec fun PsiSubstitutor.recursiveSubstitute(type: PsiType, recursionDepth: Int = 20): PsiType {
  if (recursionDepth == 0) {
    return type.accept(object : PsiTypeMapper() {
      override fun visitClassType(classType: PsiClassType): PsiType {
        return classType.rawType()
      }
    })
  }
  val substituted = substitute(type)
  return if (substituted == type) {
    type
  }
  else {
    recursiveSubstitute(substituted, recursionDepth - 1)
  }
}

class UnreachableException : RuntimeException("This statement is unreachable")

fun unreachable(): Nothing {
  throw UnreachableException()
}

fun <T, U> cartesianProduct(leftRange: Iterable<T>, rightRange: Iterable<U>): List<Pair<T, U>> =
  leftRange.flatMap { left -> rightRange.map { left to it } }

fun PsiType?.isTypeParameter(): Boolean {
  return this.resolve() is PsiTypeParameter
}

fun PsiType?.typeParameter(): PsiTypeParameter? {
  return this.resolve() as? PsiTypeParameter
}

fun findOverridableMethod(method: GrMethod): PsiMethod? {
  val clazz = method.containingClass ?: return null
  if (method.project.isDefault) {
    return null
  }
  val superMethods = method.findSuperMethods()
  val hasJavaLangOverride = method.annotations.any { it.qualifiedName == JAVA_LANG_OVERRIDE }
  if (hasJavaLangOverride && superMethods.isNotEmpty()) {
    return superMethods.first()
  }
  val candidateMethodsDomain = if (hasJavaLangOverride) {
    clazz.supers
  }
  else {
    clazz.interfaces
  }
  val alreadyOverriddenMethods = (clazz.supers + clazz)
    .flatMap { it.findMethodsByName(method.name, true).asIterable() }
    .flatMap { it.findSuperMethods().asIterable() }
  return candidateMethodsDomain
    .flatMap { it.findMethodsByName(method.name, true).asIterable() }
    .subtract(alreadyOverriddenMethods)
    .firstOrNull { methodsAgree(it, method) }
}

private fun methodsAgree(pattern: PsiMethod,
                         tested: GrMethod): Boolean {
  if (pattern.name != tested.name || tested.parameterList.parametersCount != pattern.parameterList.parametersCount) {
    return false
  }
  val parameterList: List<Pair<JvmParameter?, GrParameter?>> = pattern.parameters.zip(tested.parameters)
  return parameterList.all { (patternParameter: JvmParameter?, testedParameter: GrParameter?) ->
    if (testedParameter?.typeElement == null) return@all true
    val patternType: JvmType? = patternParameter?.type
    val erasedPatternType = if (patternType is PsiType) patternType.erasure() else patternType
    val erasedTestedType = testedParameter.type.erasure()
    erasedPatternType == erasedTestedType
  }
}

private fun PsiType.erasure(): PsiType {
  return TypeConversionUtil.erasure(this)
}

private fun getContainingClasses(startClass: PsiClass?): List<PsiClass> {
  fun getContainingClassesMutable(startClass: PsiClass?): MutableList<PsiClass> {
    startClass ?: return mutableListOf()
    val enclosingClass = startClass.containingClass ?: return mutableListOf(startClass)
    return getContainingClassesMutable(enclosingClass).apply { add(startClass) }
  }
  return getContainingClassesMutable(startClass)
}

private fun buildVirtualEnvironmentForMethod(method: GrMethod, newTypeParameterListText: String?, omitBody: Boolean): Pair<String, Int>? {
  val text = method.containingFile?.takeIf { it is GroovyFile }?.text ?: return null
  val containingClasses = getContainingClasses(method.containingClass)
  val classRepresentations = mutableListOf<String>()
  val fieldRepresentations = mutableListOf<String>()
  for (containingClass in containingClasses) {
    fieldRepresentations.add(containingClass.fields.joinToString("\n") { (it.typeElement?.text ?: "def") + " " + it.text })
    val startOffset = containingClass.textRange.startOffset
    val lBraceOffset = containingClass.lBrace?.textOffset ?: startOffset
    classRepresentations.add(text.substring(startOffset until lBraceOffset))
  }
  val header = classRepresentations.zip(fieldRepresentations)
    .joinToString("") { (classDef, fields) -> "$classDef {\n $fields \n " }
  val footer = " } ".repeat(containingClasses.size)
  val methodText = if (omitBody) {
    method.text?.removeSuffix(method.block?.text ?: "")
  }
  else {
    method.text
  } ?: return null
  val resultMethodText = insertTypeParameterList(method, methodText, newTypeParameterListText)
  return header + resultMethodText + footer to (header.length)
}

@Suppress("UnnecessaryVariable")
private fun insertTypeParameterList(method: GrMethod, methodText: String, newTypeParameterListText: String?): String {
  val methodStartOffset: Int = method.startOffset
  val typeParameterList: TextRange? = method.typeParameterList?.textRange?.takeIf { !it.isEmpty }
  val resultMethodText =
    if (typeParameterList != null && newTypeParameterListText != null) {
      val startOffset = typeParameterList.startOffset - methodStartOffset
      val erasedText = methodText.removeRange(startOffset, typeParameterList.endOffset - methodStartOffset)
      erasedText.insert(startOffset, newTypeParameterListText)
    }
    else if (typeParameterList == null) {
      var curtext = methodText
      val insertionOffset = if (method.modifierList.modifierFlags == 0) {
        curtext = "def $curtext"
        3
      }
      else {
        method.firstChild.endOffset - methodStartOffset
      }
      val actualTypeParameterListText = newTypeParameterListText ?: "<>"
      curtext.insert(insertionOffset, actualTypeParameterListText)
    }
    else {
      methodText
    }
  return resultMethodText
}

private fun String.insert(position: Int, content: String): String {
  return "${take(position)}$content${drop(position)}"
}

fun createVirtualMethod(method: GrMethod, typeParameterList: PsiTypeParameterList? = null, omitBody: Boolean = false): SmartPsiElementPointer<GrMethod>? {
  val (fileText, offset) = buildVirtualEnvironmentForMethod(method, typeParameterList?.text, omitBody) ?: return null
  val factory = GroovyPsiElementFactory.getInstance(method.project)
  val newFile = factory.createGroovyFile(fileText, false, method)
  val virtualMethod = newFile.findElementAt(offset)?.parentOfType<GrMethod>() ?: return null
  disableInteriorReturnTypeInference(virtualMethod)
  return SmartPointerManager.createPointer(virtualMethod)
}

private fun disableInteriorReturnTypeInference(virtualMethod: GrMethod) {
  virtualMethod.putUserData(forbidInteriorReturnTypeInference, Unit)
}

fun convertToGroovyMethod(method: PsiMethod): GrMethod? {
  // because method may be ClsMethod
  val factory = GroovyPsiElementFactory.getInstance(method.project)
  return if (method.isConstructor) {
    val constructorBody = method.body?.text ?: return null
    factory.createConstructorFromText(method.name, constructorBody, method)
  }
  else {
    val methodText = method.text ?: return null
    factory.createMethodFromText(methodText, method)
  }
}

fun PsiType?.resolve(): PsiClass? = (this as? PsiClassType)?.resolve()

fun PsiSubstitutor.removeForeignTypeParameters(method: GrMethod): PsiSubstitutor {
  val typeParameters = mutableListOf<PsiTypeParameter>()
  val substitutions = mutableListOf<PsiType>()
  val allowedTypeParameters = method.typeParameters.asList()
  val factory = GroovyPsiElementFactory.getInstance(method.project)
  val unboundedWildcard = PsiWildcardType.createUnbounded(method.manager)

  class ForeignTypeParameterEraser : PsiTypeMapper() {
    override fun visitClassType(classType: PsiClassType): PsiType? {
      val typeParameter = classType.typeParameter()
      if (typeParameter != null && typeParameter !in allowedTypeParameters) {
        return (compress(typeParameter.extendsListTypes.asList()) ?: getJavaLangObject(method)).accept(this)
      }
      else {
        val resolvedClass = classType.resolve() ?: return null
        val classParameters = classType.parameters.map { it?.accept(this) ?: unboundedWildcard }.toTypedArray()
        return factory.createType(resolvedClass, *classParameters)
      }
    }

    override fun visitIntersectionType(intersectionType: PsiIntersectionType): PsiType? {
      return compress(intersectionType.conjuncts.filterNotNull().mapNotNull { it.accept(this) })
    }

    override fun visitWildcardType(wildcardType: PsiWildcardType): PsiType {
      val bound = wildcardType.bound?.accept(this) ?: return wildcardType
      return when {
        wildcardType.isExtends -> PsiWildcardType.createExtends(method.manager, bound)
        wildcardType.isSuper -> PsiWildcardType.createSuper(method.manager, bound)
        else -> wildcardType
      }
    }

  }

  for ((typeParameter, type) in substitutionMap.entries) {
    typeParameters.add(typeParameter)
    substitutions.add(type.accept(ForeignTypeParameterEraser()) ?: PsiType.NULL)
  }
  return PsiSubstitutor.EMPTY.putAll(typeParameters.toTypedArray(), substitutions.toTypedArray())
}


fun compress(types: List<PsiType>?): PsiType? {
  types ?: return null
  return when {
    types.isEmpty() -> PsiType.NULL
    types.size == 1 -> types.single()
    else -> PsiIntersectionType.createIntersection(types)
  }
}

fun allOuterTypeParameters(method: PsiMethod): List<PsiTypeParameter> =
  method.typeParameters.asList() + (getContainingClasses(method.containingClass).flatMap { it.typeParameters.asList() })

fun createVirtualToActualSubstitutor(virtualMethod: GrMethod, originalMethod: GrMethod): PsiSubstitutor {
  val virtualTypeParameters = allOuterTypeParameters(virtualMethod)
  val originalTypeParameters = allOuterTypeParameters(originalMethod)
  var substitutor = PsiSubstitutor.EMPTY
  virtualTypeParameters.forEach { virtualParameter ->
    val originalParameter = originalTypeParameters.find { it.name == virtualParameter.name } ?: return@forEach
    substitutor = substitutor.put(virtualParameter, originalParameter.type())
  }
  return substitutor
}


fun PsiTypeParameter.upperBound(): PsiType =
  when (extendsListTypes.size) {
    0 -> getJavaLangObject(this)
    1 -> extendsListTypes.single()
    else -> PsiIntersectionType.createIntersection(*extendsListTypes)
  }

fun PsiElement.properResolve(): GroovyResolveResult? {
  return when (this) {
    is GrAssignmentExpression -> (lValue as? GrReferenceExpression)?.lValueReference?.advancedResolve()
    is GrConstructorInvocation -> advancedResolve()
    else -> (this as? GrCall)?.advancedResolve()
  }
}

private fun locateMethod(file: GroovyFileBase, method: GrMethod): GrMethod? {
  val containingClass = method.containingClass
  if (containingClass == null) {
    return file.methods.find { methodsAgree(it, method) }
  }
  else {
    val outerClasses = containingClass.parentsOfType<PsiClass>(true).toList().reversed()
    val initialClass = file.classes.find { it.name == outerClasses.first().name } ?: return null
    val innermostClass = outerClasses.drop(1).fold(initialClass) { currentOriginalClass, psiClass ->
      currentOriginalClass.innerClasses.find { it.name == psiClass.name } ?: return null
    }
    return innermostClass.methods.find { it.name == method.name } as? GrMethod
  }
}

@Suppress("RemoveExplicitTypeArguments")
internal fun getOriginalMethod(method: GrMethod): GrMethod {
  return when (val originalFile = method.containingFile?.originalFile) {
    null -> method
    method.containingFile -> method
    is GroovyFileBase -> locateMethod(originalFile, method) ?: method
    else -> originalFile.findElementAt(method.textOffset)?.parentOfType<GrMethod>()?.takeIf { it.name == method.name } ?: method
  }
}

private fun getFileScope(method: GrMethod): SearchScope? {
  val originalMethod = getOriginalMethod(method)
  return originalMethod.containingFile?.let { LocalSearchScope(arrayOf(it), null, true) }
}

fun getSearchScope(method: GrMethod, shouldUseReducedScope: Boolean): SearchScope? = if (shouldUseReducedScope) {
  getFileScope(method)
}
else {
  GlobalSearchScope.allScope(method.project)
}
