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
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition

class TransformationResult(
  val methods: Array<PsiMethod>,
  val fields: Array<GrField>,
  val innerClasses: Array<PsiClass>,
  val implementsTypes: Array<PsiClassType>,
  val extendsTypes: Array<PsiClassType>,
  val modifiers: Map<GrModifierList, List<String>>
)

private val emptyTransformationResult = TransformationResult(
  PsiMethod.EMPTY_ARRAY,
  GrField.EMPTY_ARRAY,
  PsiClass.EMPTY_ARRAY,
  PsiClassType.EMPTY_ARRAY,
  PsiClassType.EMPTY_ARRAY,
  emptyMap()
)

private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.groovy.transformations.TransformationUtilKt")
private var ourAssertOnRecursion: Boolean = true

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
fun transformDefinition(definition: GrTypeDefinition): TransformationResult {
  return RecursionManager.doPreventingRecursion(definition, false) {
    val transformationContext = TransformationContextImpl(definition)
    val project = definition.project
    if (DumbService.isDumb(project)) {
      DumbService.getDumbAwareExtensions(project, AstTransformationSupport.EP_NAME)
    } else {
      AstTransformationSupport.EP_NAME.extensionList
    }.forEach { transformation ->
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

infix operator fun TransformationContext.plusAssign(method: PsiMethod): Unit = addMethod(method)

infix operator fun TransformationContext.plusAssign(field: GrField): Unit = addField(field)
