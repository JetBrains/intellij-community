// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.*
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.ProcessorWithHints
import com.intellij.psi.util.InheritanceUtil
import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrRecordDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.elementInfo
import org.jetbrains.plugins.groovy.lang.psi.util.isCompactConstructor
import org.jetbrains.plugins.groovy.lang.resolve.*
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.processNonCodeMembers
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyConstructorResult
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolveKind

fun getAllConstructorResults(type: PsiClassType, place: PsiElement): Collection<GroovyResolveResult> {
  val clazz = type.resolve() ?: return emptyList()
  return getAllConstructors(clazz, place).toResolveResults()
}

fun Collection<PsiMethod>.toResolveResults(): Collection<GroovyResolveResult> = map(::ElementResolveResult)

fun getAllConstructors(clazz: PsiClass, place: PsiElement): List<PsiMethod> {
  return classConstructors(clazz) +
         runtimeConstructors(clazz, place)
}

private fun classConstructors(clazz: PsiClass): List<PsiMethod> {
  val constructors = if (clazz is GrRecordDefinition) {
    clazz.constructors.filter { !(it is GrMethod && it.isCompactConstructor()) }
  }
  else {
    clazz.constructors.asList()
  }
  if (constructors.isEmpty() && !clazz.isInterface) {
    return listOf(getDefaultConstructor(clazz))
  }
  else {
    return constructors
  }
}

private fun runtimeConstructors(clazz: PsiClass, place: PsiElement): List<PsiMethod> {
  val name = clazz.name ?: return emptyList()
  val processor = ConstructorProcessor(name)
  val qualifierType = JavaPsiFacade.getElementFactory(clazz.project).createType(clazz)
  processNonCodeMembers(qualifierType, processor, place, ResolveState.initial())
  return processor.candidates
}

private class ConstructorProcessor(private val name: String) : ProcessorWithHints(), NameHint, GroovyResolveKind.Hint, ElementClassHint {

  init {
    hint(NameHint.KEY, this)
    hint(GroovyResolveKind.HINT_KEY, this)
    hint(ElementClassHint.KEY, this)
  }

  override fun getName(state: ResolveState): String? = name

  override fun shouldProcess(kind: GroovyResolveKind): Boolean = kind == GroovyResolveKind.METHOD

  override fun shouldProcess(kind: ElementClassHint.DeclarationKind): Boolean = kind == ElementClassHint.DeclarationKind.METHOD

  private val myCandidates = SmartList<PsiMethod>()

  override fun execute(element: PsiElement, state: ResolveState): Boolean {
    if (element !is PsiMethod) {
      if (state[sorryCannotKnowElementKind] == true) {
        return true
      }
      else {
        error("Unexpected element. ${elementInfo(element)}")
      }
    }
    if (!element.isConstructor) {
      return true
    }
    myCandidates += element
    return true
  }

  val candidates: List<PsiMethod> get() = myCandidates
}


/**
 * @see org.codehaus.groovy.runtime.InvokerHelper.invokeConstructorOf
 * @see groovy.lang.MetaClassImpl.invokeConstructor(java.lang.Class, java.lang.Object[])
 */
fun resolveConstructor(
  clazz: PsiClass,
  substitutor: PsiSubstitutor,
  arguments: Arguments,
  place: PsiElement
): Collection<GroovyResolveResult> {
  return chooseConstructors(
    getAllConstructors(clazz, place),
    arguments,
    true,
    withArguments(place, substitutor, false)
  )
}

typealias WithArguments = (arguments: Arguments, mapConstructor: Boolean) -> (constructor: PsiMethod) -> GroovyMethodResult

fun withArguments(place: PsiElement, substitutor: PsiSubstitutor, needsInference: Boolean): WithArguments {
  return withArguments(place, substitutor, resultProducer(needsInference))
}

private typealias ResultProducer = (
  constructor: PsiMethod,
  place: PsiElement,
  state: ResolveState,
  arguments: Arguments,
  mapConstructor: Boolean
) -> GroovyMethodResult

private fun resultProducer(needsInference: Boolean): ResultProducer {
  return if (needsInference) {
    INFERENCE_RESULT_PRODUCER
  }
  else {
    DEFAULT_RESULT_PRODUCER
  }
}

/**
 * Creates resolve result which always runs inference.
 * Used when the constructor result was obtained from class with diamond type.
 */
private val INFERENCE_RESULT_PRODUCER: ResultProducer = ::ConstructorResolveResult

/**
 * Checks if the constructor has type parameters to infer.
 */
private val DEFAULT_RESULT_PRODUCER: ResultProducer = { constructor: PsiMethod, place: PsiElement, state: ResolveState, arguments: Arguments, mapConstructor: Boolean ->
  if (constructor.typeParameters.isNotEmpty()) {
    ConstructorResolveResult(constructor, place, state, arguments, mapConstructor)
  }
  else {
    BaseConstructorResolveResult(constructor, place, state, arguments, mapConstructor)
  }
}

/**
 * Curries [producer] with [place] and [ResolveState] with [substitutor]
 */
private fun withArguments(place: PsiElement, substitutor: PsiSubstitutor, producer: ResultProducer): WithArguments {
  val state = ResolveState.initial().put(PsiSubstitutor.KEY, substitutor)
  return { arguments: Arguments, mapConstructor: Boolean ->
    { constructor: PsiMethod ->
      producer(constructor, place, state, arguments, mapConstructor)
    }
  }
}

/**
 * Main algorithm.
 */
fun chooseConstructors(constructors: List<PsiMethod>,
                       arguments: Arguments,
                       allowMapConstructor: Boolean,
                       withArguments: WithArguments): List<GroovyMethodResult> {
  if (constructors.isEmpty()) {
    return emptyList()
  }
  val (applicable: Boolean, results: List<GroovyMethodResult>) = chooseConstructors(
    constructors,
    withArguments(arguments, false)
  )
  if (!allowMapConstructor) {
    return results
  }
  if (applicable) {
    return results
  }
  val singleArgument: Argument? = arguments.singleOrNull()
  if (singleArgument == null) {
    return results
  }
  if (!InheritanceUtil.isInheritor(singleArgument.runtimeType, CommonClassNames.JAVA_UTIL_MAP)) {
    return results
  }
  val (noArgApplicable: Boolean, noArgResults: List<GroovyMethodResult>) = chooseConstructors(
    constructors,
    withArguments(emptyList(), true)
  )
  if (noArgApplicable) {
    return noArgResults
  }
  return results
}

private data class ApplicabilityResult(val applicable: Boolean, val results: List<GroovyMethodResult>)

private fun chooseConstructors(constructors: List<PsiMethod>, result: (constructor: PsiMethod) -> GroovyMethodResult): ApplicabilityResult {
  val results: List<GroovyMethodResult> = constructors.map(result)
  val applicableResults: List<GroovyMethodResult>? = chooseConstructors(results)
  return ApplicabilityResult(applicableResults != null, applicableResults ?: results)
}

/**
 * @return applicable results or `null` if there are no applicable results
 */
private fun chooseConstructors(results: List<GroovyMethodResult>): List<GroovyMethodResult>? {
  val applicable = results.filterTo(SmartList()) {
    it.checkMapConstructor() && it.isApplicable
  }
  if (applicable.isNotEmpty()) {
    return chooseOverloads(applicable)
  }
  else {
    return null
  }
}

private fun GroovyMethodResult.checkMapConstructor(): Boolean =
  if (this is GroovyConstructorResult && isMapConstructor) candidate?.method?.parameters?.size == 0 else true

