// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.style

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.fixes.RemoveElementWithoutFormatterFix
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets.WHITE_SPACES_OR_COMMENTS
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.skipSet
import org.jetbrains.plugins.groovy.util.TokenSet
import org.jetbrains.plugins.groovy.util.minus
import org.jetbrains.plugins.groovy.util.plus

class GrUnnecessarySemicolonInspection : LocalInspectionTool(), CleanupLocalInspectionTool {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
    override fun visitElement(element: PsiElement) {
      if (element.node.elementType !== T_SEMI || Holder.isSemicolonNecessary(element)) return
      holder.registerProblem(
        element,
        GroovyBundle.message("unnecessary.semicolon.description"),
        Holder.fix
      )
    }
  }

  private object Holder {
    val fix: LocalQuickFix = RemoveElementWithoutFormatterFix(GroovyBundle.message("unnecessary.semicolon.fix"))

    private val nlSet = TokenSet(NL)
    private val forwardSet = WHITE_SPACES_OR_COMMENTS + TokenSet(T_SEMI) - nlSet
    private val backwardSet = WHITE_SPACES_OR_COMMENTS - nlSet
    private val separators = TokenSet(NL, T_SEMI)
    private val previousSet = TokenSet(T_LBRACE, T_ARROW)

    fun isSemicolonNecessary(semicolon: PsiElement): Boolean {
      if (semicolon.parent is GrTraditionalForClause) return true

      val prevSibling = skipSet(semicolon, false, backwardSet) ?: return false
      val nextSibling = skipSet(semicolon, true, forwardSet) ?: return false

      val prevType = prevSibling.node.elementType

      return when {
        prevType in separators -> {
          prevSibling.prevSibling is GrEnumConstantList
        }
        prevType in previousSet -> {
          false
        }
        prevSibling is GrStatement -> {
          nextSibling is GrStatement || nextSibling.nextSibling is GrClosableBlock
        }
        prevSibling is GrPackageDefinition || prevSibling is GrImportStatement -> {
          nextSibling.node.elementType !in separators
        }
        prevSibling is GrParameterList && prevSibling.parent is GrClosableBlock -> {
          false // beginning of a closure
        }
        else -> true
      }
    }
  }
}
