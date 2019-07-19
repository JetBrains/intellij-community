// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariablesOrder
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitNode
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession


class NameGenerator(private val restrictions: Collection<String> = emptySet()) {
  companion object {
    private const val nameRange = 'Z'.toByte() - 'T'.toByte()

    private fun produceTypeParameterName(index: Int): String {
      return ('T'.toByte() + index % nameRange).toChar().toString() + (index / nameRange).toString()
    }
  }

  private var counter = 0

  val name: String
    get() {
      while (true) {
        val name = produceTypeParameterName(counter)
        ++counter
        if (name !in restrictions) {
          return name
        }
      }
    }

}

typealias InferenceGraphNode = InferenceVariablesOrder.InferenceGraphNode<InferenceUnitNode>

fun getInferenceVariable(session: GroovyInferenceSession, variableType: PsiType): InferenceVariable {
  return session.getInferenceVariable(session.substituteWithInferenceVariables(variableType))
}

fun Iterable<PsiType>.flattenIntersections(): Iterable<PsiType> {
  return this.flatMap { if (it is PsiIntersectionType) it.conjuncts.asIterable() else listOf(it) }
}

fun GroovyPsiElementFactory.createProperTypeParameter(name: String, superTypes: Array<out PsiClassType>): PsiTypeParameter {
  val extendsBound =
    if (superTypes.size > 1 || superTypes.size == 1 && !superTypes[0].equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
      val filteredSupertypes = superTypes.filter { !it.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) }
      " extends ${filteredSupertypes.joinToString("&") { it.getCanonicalText(true) }}"
    }
    else {
      ""
    }
  val method = "public <$name $extendsBound> void foo(){}"
  return createMethodFromText(method, null).typeParameters.single()
}

fun PsiType.ensureWildcards(factory: GroovyPsiElementFactory, manager: PsiManager): PsiType {
  val enclosing = this
  return accept(object : PsiTypeMapper() {
    override fun visitClassType(classType: PsiClassType?): PsiType? {
      classType ?: return classType
      val mappedParameters = classType.parameters.map { it.accept(this) }
      val newType = factory.createType(classType.resolve()!!, *mappedParameters.toTypedArray())
      if (classType == enclosing) {
        return newType
      }
      else {
        return PsiWildcardType.createExtends(manager, newType)
      }
    }

  })
}

fun PsiType?.isClosureType(): Boolean {
  return (this as? PsiClassType)?.rawType()?.equalsToText(GroovyCommonClassNames.GROOVY_LANG_CLOSURE) ?: false
}

fun PsiSubstitutor.recursiveSubstitute(type: PsiType, recursionDepth: Int = 20): PsiType {
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


fun collectDependencies(typeParameterList: PsiTypeParameterList,
                        resultSubstitutor: PsiSubstitutor): Map<PsiTypeParameter, List<PsiTypeParameter>> {
  class LocalVisitor : PsiTypeVisitor<Unit>() {
    val collector = mutableListOf<PsiTypeParameter>()
    override fun visitClassType(classType: PsiClassType?) {
      classType ?: return
      val clazz = classType.resolve()
      if (clazz is PsiTypeParameter) {
        if (clazz !in collector) {
          collector.add(clazz)
          resultSubstitutor.substitute(clazz)!!.accept(this)
        }
      }
      else {
        classType.parameters.forEach { it.accept(this) }
      }
      super.visitClassType(classType)
    }

    override fun visitWildcardType(wildcardType: PsiWildcardType?) {
      wildcardType ?: return
      wildcardType.bound?.accept(this)
      super.visitWildcardType(wildcardType)
    }

    fun collect(type: PsiType): List<PsiTypeParameter> {
      type.accept(this)
      return collector
    }
  }

  return typeParameterList.typeParameters.map { it to LocalVisitor().collect(resultSubstitutor.substitute(it)!!) }.toMap()
}

fun PsiType?.isTypeParameter(): Boolean {
  return this.resolve() is PsiTypeParameter
}

fun PsiType?.typeParameter(): PsiTypeParameter? {
  return this.resolve() as? PsiTypeParameter
}

fun findOverridableMethod(method: GrMethod): PsiMethod? {
  val clazz = method.containingClass ?: return null
  val candidateMethodsDomain = if (method.annotations.any { it.qualifiedName == CommonClassNames.JAVA_LANG_OVERRIDE }) {
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
    testedParameter.typeElement == null || (testedParameter.type as PsiClassType).erasure() == (patternParameter.type as PsiClassType).erasure()
  }
}

fun PsiClassType.erasure(): PsiClassType {
  val raw = rawType()
  val typeParameter = raw.typeParameter()
  return if (typeParameter != null) {
    typeParameter.extendsListTypes.firstOrNull()?.erasure() ?: PsiType.getJavaLangObject(typeParameter.manager, typeParameter.resolveScope)
  }
  else {
    raw
  }
}

fun createVirtualMethod(method: GrMethod): GrMethod {
  val elementFactory = GroovyPsiElementFactory.getInstance(method.project)
  val virtualMethod = if (method.isConstructor) {
    elementFactory.createConstructorFromText(method.name, method.text, method)
  }
  else {
    elementFactory.createMethodFromText(method.text, method)
  }
  if (!virtualMethod.hasTypeParameters()) {
    virtualMethod.addAfter(elementFactory.createTypeParameterList(), virtualMethod.firstChild)
  }
  return virtualMethod
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