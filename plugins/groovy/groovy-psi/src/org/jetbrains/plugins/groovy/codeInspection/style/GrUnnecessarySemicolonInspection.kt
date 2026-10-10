// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.style

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.fixes.RemoveElementWithoutFormatterFix
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets.WHITE_SPACES_OR_COMMENTS
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.NL
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_ARROW
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_LBRACE
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_SEMI
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLoopStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
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
        RemoveElementWithoutFormatterFix(GroovyBundle.message("unnecessary.semicolon.fix"))
      )
    }
  }

  private object Holder {
    private val forwardSet = WHITE_SPACES_OR_COMMENTS + TokenSet(T_SEMI) - TokenSet(NL)
    private val separators = TokenSet(NL, T_SEMI)
    private val previousSet = TokenSet(T_LBRACE, T_ARROW)

    fun isSemicolonNecessary(semicolon: PsiElement): Boolean {
      val parent = semicolon.parent
      if (parent is GrTraditionalForClause || parent is GrIfStatement || parent is GrLoopStatement) return true

      val prevSibling = PsiTreeUtil.skipWhitespacesAndCommentsBackward(semicolon) ?: return false
      val nextSibling = skipSet(semicolon, true, forwardSet) ?: return false

      val prevType = prevSibling.node.elementType

      return when {
        prevType in separators -> PsiTreeUtil.skipWhitespacesAndCommentsBackward(prevSibling) is GrEnumConstantList
        prevType in previousSet -> false
        prevSibling is GrCall && skipSet(nextSibling, true, WHITE_SPACES_OR_COMMENTS) is GrBlockStatement -> true
        prevSibling is GrStatement -> nextSibling is GrStatement || nextSibling.nextSibling is GrClosableBlock
        prevSibling is GrPackageDefinition || prevSibling is GrImportStatement -> nextSibling.node.elementType !in separators
        prevSibling is GrParameterList && prevSibling.parent is GrClosableBlock -> false // beginning of a closure
        else -> true
      }
    }
  }
}
