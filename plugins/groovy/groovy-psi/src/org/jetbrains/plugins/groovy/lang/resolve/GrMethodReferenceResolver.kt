// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightMethodBuilder
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMethodReferenceExpressionImpl
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.getConstructorCandidates
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.unwrapClassType
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodReferenceProcessor
import org.jetbrains.plugins.groovy.lang.resolve.processors.StaticMethodReferenceProcessor

internal object GrMethodReferenceResolver : GroovyResolver<GrMethodReferenceExpressionImpl> {

  override fun resolve(ref: GrMethodReferenceExpressionImpl, incomplete: Boolean): Collection<GroovyResolveResult> {
    val name = ref.referenceName ?: return emptyList()
    val type = ref.qualifier?.type ?: return emptyList()

    val instanceContext = run {
      val processor = MethodReferenceProcessor(name)
      type.processReceiverType(processor, ResolveState.initial(), ref)
      processor.results
    }

    val unwrapped = unwrapClassType(type) ?: return instanceContext

    val staticContext = run {
      val processor = StaticMethodReferenceProcessor(name)
      unwrapped.processReceiverType(processor, ResolveState.initial(), ref)
      processor.results
    }

    val constructors = when (unwrapped) {
      is PsiClassType -> getConstructorCandidates(unwrapped, null, ref).toList()
      is PsiArrayType -> fakeArrayConstructors(unwrapped, ref.manager)
      else -> emptyList()
    }

    return instanceContext + staticContext + constructors
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
