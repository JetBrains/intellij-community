// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.hint.types

import com.intellij.codeInsight.hints.JavaTypeHintsFactory
import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiType
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeAugmenter
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrVariableEnhancer

class GroovyLambdaParameterTypeHintsInlayProvider : InlayHintsProvider {
  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector {
    return object : SharedBypassCollector {
      override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
        if (element !is GrParameter || element.typeElement != null || element.isVarArgs) return
        val type: PsiType = getRepresentableType(element) ?: return
        sink.addPresentation(InlineInlayPosition(element.textOffset, relatedToPrevious = true), hasBackground = true) {
          JavaTypeHintsFactory.typeHint(type, this)
          text(" ")
        }
      }
    }
  }

  private fun getRepresentableType(variable: GrParameter): PsiType? {
    var inferredType: PsiType? = GrVariableEnhancer.getEnhancedType(variable)
    if (inferredType == null) {
      val ownerFlow = variable.parentOfType<GrControlFlowOwner>()?.controlFlow ?: return null
      if (TypeInferenceHelper.isSimpleEnoughForAugmenting(ownerFlow)) {
        inferredType = TypeAugmenter.inferAugmentedType(variable)
      }
    }
    return inferredType?.takeIf { !it.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) }
  }
}