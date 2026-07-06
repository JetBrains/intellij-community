// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.groovy.spock

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentsOfType
import org.jetbrains.plugins.groovy.editor.GroovyInlayHintFilter
import org.jetbrains.plugins.groovy.ext.spock.isOr
import org.jetbrains.plugins.groovy.ext.spock.isTableColumnSeparator
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression

class SpockTableInlayHintFilter : GroovyInlayHintFilter {

  override fun shouldHideHints(element: PsiElement): Boolean {
    val maybeSeparator = element.parentsOfType<GrBinaryExpression>().firstOrNull { it.isOr() }
    return maybeSeparator != null && maybeSeparator.isTableColumnSeparator()
  }
}
