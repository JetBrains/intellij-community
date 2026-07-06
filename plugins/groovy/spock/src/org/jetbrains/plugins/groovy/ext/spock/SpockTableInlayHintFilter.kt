// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.spock

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentsOfType
import org.jetbrains.plugins.groovy.editor.GroovyInlayHintFilter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression

class SpockTableInlayHintFilter : GroovyInlayHintFilter {

  override fun shouldHideHints(element: PsiElement): Boolean {
    val maybeSeparator = element.parentsOfType<GrBinaryExpression>().firstOrNull { it.isOr() }
    return maybeSeparator != null && maybeSeparator.isTableColumnSeparator()
  }
}
