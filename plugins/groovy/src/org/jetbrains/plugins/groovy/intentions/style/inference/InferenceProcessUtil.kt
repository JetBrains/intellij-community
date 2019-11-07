// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT
import com.intellij.psi.CommonClassNames.JAVA_LANG_OVERRIDE
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariablesOrder
import com.intellij.psi.util.parentOfType
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_OBJECT
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.putAll
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import org.jetbrains.plugins.groovy.lang.typing.box


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
      " extends ${filteredSupertypes.joinToString("&") { it.getCanonicalText(true) }}"
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
    override fun visitClassType(classType: PsiClassType?): PsiType? {
      classType ?: return classType
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
        return PsiWildcardType.createUnbounded(manager)
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
      override fun visitClassType(classType: PsiClassType?): PsiType? {
        return classType?.rawType()
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
  if (tested.parameterList.parametersCount != pattern.parameterList.parametersCount) {
    return false
  }
  val parameterList = pattern.parameters.zip(tested.parameters)
  return parameterList.all { (patternParameter, testedParameter) ->
    val boxedPatternParameter = (patternParameter.type as? PsiPrimitiveType)?.box(tested) ?: patternParameter.type
    testedParameter.typeElement == null ||
    (testedParameter.type.box(tested) as PsiClassType).erasure() == (boxedPatternParameter as? PsiClassType)?.erasure()
  }
}

fun PsiClassType.erasure(): PsiClassType {
  val raw = rawType()
  val typeParameter = raw.typeParameter()
  return if (typeParameter != null) {
    typeParameter.extendsListTypes.firstOrNull()?.erasure() ?: getJavaLangObject(typeParameter)
  }
  else {
    raw
  }
}

fun createVirtualMethod(method: GrMethod, typeParameterList: PsiTypeParameterList? = null): GrMethod? {
  val virtualFile = method.containingFile.copy() as? GroovyFile ?: return null
  val newMethod = virtualFile.findElementAt(method.textOffset)?.parentOfType<GrMethod>() ?: return null
  if (newMethod.hasTypeParameters()) {
    if (typeParameterList != null) {
      newMethod.typeParameterList!!.replace(typeParameterList)
    }
  }
  else {
    if (typeParameterList != null) {
      newMethod.addAfter(typeParameterList, newMethod.firstChild)
    }
    else {
      newMethod.addAfter(GroovyPsiElementFactory.getInstance(virtualFile.project).createTypeParameterList(), newMethod.firstChild)
    }
  }
  return newMethod
}

fun convertToGroovyMethod(method: PsiMethod): GrMethod {
  // because method may be ClsMethod
  val factory = GroovyPsiElementFactory.getInstance(method.project)
  return if (method.isConstructor) {
    factory.createConstructorFromText(method.name, method.text, method)
  }
  else {
    factory.createMethodFromText(method.text, method)
  }
}

fun PsiType?.resolve(): PsiClass? = (this as? PsiClassType)?.resolve()

fun PsiSubstitutor.removeForeignTypeParameters(method: GrMethod): PsiSubstitutor {
  val typeParameters = mutableListOf<PsiTypeParameter>()
  val substitutions = mutableListOf<PsiType>()
  val allowedTypeParameters = method.typeParameters.asList()
  val factory = GroovyPsiElementFactory.getInstance(method.project)

  class ForeignTypeParameterEraser : PsiTypeMapper() {
    override fun visitClassType(classType: PsiClassType?): PsiType? {
      classType ?: return classType
      val typeParameter = classType.typeParameter()
      if (typeParameter != null && typeParameter !in allowedTypeParameters) {
        return (compress(typeParameter.extendsListTypes.asList()) ?: getJavaLangObject(method)).accept(this)
      }
      else {
        return factory.createType(classType.resolve() ?: return null, *classType.parameters.mapNotNull { it.accept(this) }.toTypedArray())
      }
    }

    override fun visitIntersectionType(intersectionType: PsiIntersectionType?): PsiType? {
      return compress(intersectionType?.conjuncts?.filterNotNull()?.mapNotNull { it.accept(this) })
    }

    override fun visitWildcardType(wildcardType: PsiWildcardType?): PsiType? {
      return when {
        wildcardType == null -> null
        wildcardType.isExtends -> PsiWildcardType.createExtends(method.manager, wildcardType.bound!!.accept(this))
        wildcardType.isSuper -> PsiWildcardType.createSuper(method.manager, wildcardType.bound!!.accept(this))
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
  method.typeParameters.asList() + (method.containingClass?.run { listOf(this) + supers }?.flatMap { it.typeParameters.asList() }
                                    ?: emptyList())

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

@Suppress("RemoveExplicitTypeArguments")
internal fun getOriginalMethod(method: GrMethod): GrMethod {
  return when (val originalFile = method.containingFile?.originalFile) {
      null -> method
      method.containingFile -> method
      is GroovyFileBase -> originalFile.methods.find { methodsAgree(it, method) } ?: method
      else -> originalFile.findElementAt(method.textOffset)?.parentOfType<GrMethod>()?.takeIf { it.name == method.name } ?: method
    }
}
