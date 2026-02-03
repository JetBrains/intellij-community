// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.hint.types

import com.intellij.codeInsight.hints.JavaTypeHintsFactory
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiType
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeAugmenter
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.ClosureSyntheticParameter
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrVariableEnhancer

class GroovyLambdaParameterTypeHintsInlayProvider : InlayHintsProvider {
  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector {
    return object : SharedBypassCollector {
      override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
        collectNamedParameters(element, sink)
        collectImplicitLambdaParameter(element, sink)
      }

      private fun collectImplicitLambdaParameter(element: PsiElement, sink: InlayTreeSink) {
        if (element !is GrClosableBlock || !element.parameterList.isEmpty) return
        val parameter = element.allParameters.singleOrNull() as? ClosureSyntheticParameter ?: return
        if (!parameter.isStillValid) return
        addPresentation(element.lBrace.endOffset, parameter, sink, " it -> ")
      }

      private fun addPresentation(
        offset: Int,
        parameter: GrParameter,
        sink: InlayTreeSink,
        suffixText: String) {
        val type = getRepresentableType(parameter) ?: return
        sink.addPresentation(InlineInlayPosition(offset, relatedToPrevious = true), null, null, HintFormat.default) {
          JavaTypeHintsFactory.typeHint(type, this)
          text(suffixText)
        }
      }

      private fun collectNamedParameters(element: PsiElement, sink: InlayTreeSink) {
        if (element !is GrParameter || element.typeElement != null || element.isVarArgs) return
        addPresentation(element.textOffset, element, sink, " ")
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