// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.transformations.singleton.LightAstTransformationSupport

class TransformationResult(
  val methodInfos: Array<MethodInfo>,
  internal val myRemovedMethodInfos: Set<MethodInfo>,
  val fields: Array<GrField>,
  val innerClasses: Array<PsiClass>,
  val implementsTypes: Array<PsiClassType>,
  val extendsTypes: Array<PsiClassType>,
  val modifiers: Map<GrModifierList, List<String>>,
  internal val wasExtendsTypeSet: Boolean
)

/**
 * Stores the way method was added to [TransformationContext]
 * 1. [FIRST] - method was added to the beginning of the `TransformationResult#methodInfos` via `TransformationContext.addMethod(PsiMethod, true)`
 * 2. [DEFAULT] - method was already in the code [org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition.getCodeMethods]
 * 3. [LAST] - method was added to the end of the method list via `TransformationContext.addMethod(PsiMethod, false)`
 */
enum class MethodOrder {
  FIRST, DEFAULT, LAST,
}

/**
 * Stores information about method added to [TransformationContext]
 *
 * @property method actual method
 * @property order describes how the method was added to the method list
 */
data class MethodInfo(val method: PsiMethod, val order: MethodOrder): Comparable<MethodInfo> {
  override fun compareTo(other: MethodInfo): Int = order.compareTo(other.order)
}

/**
 * Merges two [TransformationResult]s into one preserving order for methods.
 * NB: this method assumes there are no duplicates between members [TransformationResult] to avoid recursion issues
 */
infix operator fun TransformationResult.plus(other: TransformationResult): TransformationResult {
  val removedMethodInfos = myRemovedMethodInfos + other.myRemovedMethodInfos
  val mergedMethodInfos = (methodInfos + other.methodInfos).filter {
    it !in removedMethodInfos
  }.sorted().toTypedArray<MethodInfo>()
  return TransformationResult(
    mergedMethodInfos,
    removedMethodInfos,
    fields + other.fields,
    innerClasses + other.innerClasses,
    implementsTypes + other.implementsTypes,
    mergeExtendsTypes(extendsTypes, wasExtendsTypeSet, other.extendsTypes, other.wasExtendsTypeSet),
    modifiers + other.modifiers,
    wasExtendsTypeSet || other.wasExtendsTypeSet
  )
}

private fun mergeExtendsTypes(
  first: Array<PsiClassType>,
  firstWasSet: Boolean,
  second: Array<PsiClassType>,
  secondWasSet: Boolean,
): Array<PsiClassType> {
  if (firstWasSet && secondWasSet) throw IllegalArgumentException("Custom types was set in 2 transformations, can't decided which is more appropriate")
  return if (firstWasSet) first else if (secondWasSet) second else first + second
}

private val emptyTransformationResult = TransformationResult(
  emptyArray(),
  emptySet(),
  GrField.EMPTY_ARRAY,
  PsiClass.EMPTY_ARRAY,
  PsiClassType.EMPTY_ARRAY,
  PsiClassType.EMPTY_ARRAY,
  emptyMap(),
  false
)

private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.groovy.transformations.TransformationUtilKt")
private var ourAssertOnRecursion: Boolean = true

@TestOnly
fun disableAssertOnRecursion(disposable: Disposable) {
  if (!ourAssertOnRecursion) {
    return
  }
  RecursionManager.disableMissedCacheAssertions(disposable)
  ourAssertOnRecursion = false
  Disposer.register(disposable, Disposable {
    ourAssertOnRecursion = true
  })
}

/**
 * In dumb mode only transformations that are marked as [com.intellij.openapi.project.DumbAware] are executed.
 */
@JvmOverloads
fun transformDefinition(definition: GrTypeDefinition, type: TransformationType = TransformationType.DEFAULT): TransformationResult {
  return RecursionManager.doPreventingRecursion(definition, false) {
    val transformationContext = TransformationContextImpl(definition, type != TransformationType.LIGHT)
    val project = definition.project
    if (DumbService.isDumb(project)) {
      DumbService.getDumbAwareExtensions(project, AstTransformationSupport.EP_NAME)
    } else {
      AstTransformationSupport.EP_NAME.extensionList
    }.filter {
      when (type) {
        TransformationType.LIGHT -> it is LightAstTransformationSupport
        TransformationType.RECURSIVE -> it !is LightAstTransformationSupport
        TransformationType.DEFAULT -> true
      }
    }
      .forEach { transformation ->
      ProgressManager.checkCanceled()
      transformation.applyTransformation(transformationContext)
    }
    transformationContext.transformationResult
  } ?: run {
    if (ourAssertOnRecursion) {
      LOG.error("recursion")
    }
    emptyTransformationResult
  }
}

/**
 * Represents the type of transformations that should be applied to [GrTypeDefinition]
 */
enum class TransformationType {
  /**
   * Applies all transformations. Consider avoiding this option as it may lead to stack overflow.
   */
  DEFAULT,

  /**
   * Applies only transformations marked with [LightAstTransformationSupport]
   */
  LIGHT,

  /**
   * Applies only transformations that can be calculated based on other transformations.
   */
  RECURSIVE,
}

infix operator fun TransformationContext.plusAssign(method: PsiMethod): Unit = addMethod(method)

infix operator fun TransformationContext.plusAssign(field: GrField): Unit = addField(field)
