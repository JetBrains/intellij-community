// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightMethodBuilder
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMethodReferenceExpressionImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMethodReferenceExpressionImpl.Companion.CONSTRUCTOR_REFERENCE_NAME
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.unwrapClassType
import org.jetbrains.plugins.groovy.lang.resolve.impl.getAllConstructorResults
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodReferenceProcessor

internal object GrMethodReferenceResolver : GroovyResolver<GrMethodReferenceExpressionImpl> {

  override fun resolve(ref: GrMethodReferenceExpressionImpl, incomplete: Boolean): Array<GroovyResolveResult> {
    val name = ref.referenceName ?: return GroovyResolveResult.EMPTY_ARRAY
    val type = ref.qualifier?.type ?: return GroovyResolveResult.EMPTY_ARRAY

    val methods = run {
      val processor = MethodReferenceProcessor(name)
      type.processReceiverType(processor, ResolveState.initial(), ref)
      processor.results
    }

    val unwrapped = unwrapClassType(type) ?: return methods.toTypedArray()

    val constructors = if (name == CONSTRUCTOR_REFERENCE_NAME) {
      when (unwrapped) {
        is PsiClassType -> getAllConstructorResults(unwrapped, ref).toList()
        is PsiArrayType -> fakeArrayConstructors(unwrapped, ref.manager)
        else -> emptyList()
      }
    }
    else {
      emptyList()
    }

    return (methods + constructors).toTypedArray()
  }

  private fun fakeArrayConstructors(type: PsiArrayType, manager: PsiManager): List<GroovyResolveResult> {
    return (0 until type.arrayDimensions).map { i ->
      ElementResolveResult(fakeArrayConstructor(type, manager, i))
    }
  }

  private fun fakeArrayConstructor(type: PsiArrayType, manager: PsiManager, dimensions: Int): LightMethodBuilder {
    return LightMethodBuilder(manager, GroovyLanguage, "fake array constructor").apply {
      setMethodReturnType(type)
      if (dimensions == 0) {
        addParameter("size", PsiType.INT)
      }
      else {
        for (i in 0..dimensions) {
          addParameter("size$i", PsiType.INT)
        }
      }
    }
  }
}
