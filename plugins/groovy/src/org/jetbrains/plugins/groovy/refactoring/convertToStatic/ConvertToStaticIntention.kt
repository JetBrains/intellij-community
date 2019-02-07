// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.convertToStatic

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.intentions.base.Intention
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager.checkForPass
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle

class ConvertToStaticIntention : Intention() {

  override fun getText(): String {
    return GroovyRefactoringBundle.message("intention.converting.to.static")
  }

  override fun getFamilyName(): String {
    return GroovyRefactoringBundle.message("intention.converting.to.static.family")
  }

  private fun isCSAnnotated(element: PsiElement): Boolean {
    if (element !is GrMember) return false
    val annotation = GroovyPsiManager.getCompileStaticAnnotation(element) ?: return false
    return checkForPass(annotation)
  }

  override fun processIntention(element: PsiElement, project: Project, editor: Editor?) {
    val containingMember = PsiTreeUtil.findFirstParent(element, false, ::isCSAnnotated) as? GrMember ?: return
    applyDeclarationFixes(containingMember)
    applyErrorFixes(containingMember)
  }

  override fun getElementPredicate(): PsiElementPredicate = PsiElementPredicate {
    if (!PsiUtil.isCompileStatic(it)) return@PsiElementPredicate false
    val checker = TypeChecker()
    it.accept(GroovyPsiElementVisitor(checker))
    if (checker.fixes.isNotEmpty()) return@PsiElementPredicate true

    return@PsiElementPredicate checkUnresolvedReferences(it)
  }

  private fun checkUnresolvedReferences(element: PsiElement): Boolean {
    val expression = element as? GrReferenceExpression ?: return false
    if (expression.advancedResolve().isValidResult) return false
    val qualifier = expression.qualifierExpression ?: return false
    return collectReferencedEmptyDeclarations(qualifier, false).isNotEmpty()
  }
}