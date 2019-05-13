// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util

import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.resolve.CollectClassMembersUtil.getFields
import org.jetbrains.plugins.groovy.lang.resolve.CompilationPhaseHint
import org.jetbrains.plugins.groovy.lang.resolve.CompilationPhaseHint.Phase.CONVERSION
import org.jetbrains.plugins.groovy.lang.resolve.CompilationPhaseHint.Phase.TRANSFORMATION
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.processElement
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessFields

fun GrTypeDefinition.processPhase(processor: PsiScopeProcessor, state: ResolveState): Boolean {
  val hint = requireNotNull(processor.getHint(CompilationPhaseHint.HINT_KEY))
  return when (hint.beforePhase()) {
    CONVERSION -> processBeforeConversion(processor, state)
    TRANSFORMATION -> processBeforeTransformations(processor, state)
  }
}

private fun GrTypeDefinition.processBeforeConversion(processor: PsiScopeProcessor, state: ResolveState): Boolean {
  if (processor.shouldProcessFields()) {
    for (codeField in codeFields) { // raw field nodes as appear after parsing
      if (!processElement(processor, codeField, state)) return false
    }
  }
  return true
}

private fun GrTypeDefinition.processBeforeTransformations(processor: PsiScopeProcessor, state: ResolveState): Boolean {
  if (processor.shouldProcessFields()) {
    for (codeField in getFields(this, false)) { // property nodes removed if field with same name exists
      if (!processElement(processor, codeField, state)) return false
    }
  }
  return true
}
