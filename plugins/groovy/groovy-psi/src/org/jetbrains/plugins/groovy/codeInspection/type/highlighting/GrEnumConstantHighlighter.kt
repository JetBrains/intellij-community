// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type.highlighting

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.highlighting.HighlightSink
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant

class GrEnumConstantHighlighter(
  private val enumConstant: GrEnumConstant,
  sink: HighlightSink
) : ConstructorCallHighlighter(enumConstant.constructorReference, sink) {

  override val highlightElement: PsiElement get() = enumConstant.argumentList ?: enumConstant.nameIdentifierGroovy
}
