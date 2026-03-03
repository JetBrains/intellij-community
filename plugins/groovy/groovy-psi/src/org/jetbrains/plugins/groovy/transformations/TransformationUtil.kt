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
  val modifiers: Map<GrModifierList, List<String>>
)

enum class MethodOrder{
  FIRST, DEFAULT, LAST,
}

data class MethodInfo(val method: PsiMethod, val methodOrder: MethodOrder): Comparable<MethodInfo> {
  override fun compareTo(other: MethodInfo): Int = methodOrder.compareTo(other.methodOrder)
}

infix operator fun TransformationResult.plus(other: TransformationResult): TransformationResult {
  val removedMethodInfos = myRemovedMethodInfos + other.myRemovedMethodInfos
  val mergedMethodInfos = (methodInfos + other.methodInfos).distinctBy { method -> method.hashCode() }.filter {
    it !in removedMethodInfos
  }.sorted().toTypedArray<MethodInfo>()
  return TransformationResult(
    mergedMethodInfos,
    removedMethodInfos,
    (fields + other.fields).distinctBy { field -> field.hashCode() }.toTypedArray<GrField>(),
    (innerClasses + other.innerClasses).distinctBy { clazz -> clazz.hashCode() }.toTypedArray<PsiClass>(),
    (implementsTypes + other.implementsTypes).distinctBy { type -> type.hashCode() }.toTypedArray<PsiClassType>(),
    (extendsTypes + other.extendsTypes).distinctBy { type -> type.hashCode() }.toTypedArray<PsiClassType>(),
    modifiers + other.modifiers
  )
}

private val emptyTransformationResult = TransformationResult(
  emptyArray(),
  emptySet(),
  GrField.EMPTY_ARRAY,
  PsiClass.EMPTY_ARRAY,
  PsiClassType.EMPTY_ARRAY,
  PsiClassType.EMPTY_ARRAY,
  emptyMap()
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
    val transformationContext = TransformationContextImpl(definition)
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
