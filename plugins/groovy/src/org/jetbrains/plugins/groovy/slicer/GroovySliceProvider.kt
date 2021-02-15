// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.slicer

import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.slicer.*
import com.intellij.util.CommonProcessors
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

class GroovySliceProvider : SliceLanguageSupportProvider, SliceUsageTransformer {
  object GroovySliceLeafEquality : SliceLeafEquality() {
    override fun substituteElement(element: PsiElement): PsiElement = element.getGroovyReferenceTargetOrThis()
  }

  companion object {
    private fun PsiElement.getGroovyReferenceTargetOrThis() = (this as? GrReferenceElement<*>)?.resolve() ?: this

    fun getInstance(): GroovySliceProvider = LanguageSlicing.INSTANCE.forLanguage(GroovyLanguage) as GroovySliceProvider
  }

  override fun getExpressionAtCaret(atCaret: PsiElement, dataFlowToThis: Boolean): PsiElement? {
    val element = PsiTreeUtil.getParentOfType(atCaret, GrExpression::class.java, GrVariable::class.java)
    if (dataFlowToThis && element is GrLiteral) return null
    return element
  }

  override fun getElementForDescription(element: PsiElement): PsiElement = (element as? GrReferenceElement<*>)?.resolve() ?: element

  override fun getRenderer(): GroovySliceUsageCellRenderer = GroovySliceUsageCellRenderer()

  override fun createRootUsage(element: PsiElement, params: SliceAnalysisParams): SliceUsage {
    return GroovySliceUsage(element, params)
  }

  override fun transform(sliceUsage: SliceUsage): Collection<SliceUsage>? {
    if (sliceUsage is GroovySliceUsage) return null

    val element = sliceUsage.element
    val parent = sliceUsage.parent

    if (sliceUsage.params.dataFlowToThis && element is GrMethod) {
      return CommonProcessors.CollectProcessor<SliceUsage>().apply {
        GroovySliceUsage.processMethodReturnValues(element, parent, this)
      }.results
    }

    if (!(element is GrExpression || element is GrVariable)) return null
    val parentUsage = sliceUsage.parent
    val newUsage = if (parentUsage != null) GroovySliceUsage(element, parentUsage) else GroovySliceUsage(element, sliceUsage.params)
    return listOf(newUsage)
  }

  fun createLeafAnalyzer(): SliceLeafAnalyzer = SliceLeafAnalyzer(GroovySliceLeafEquality, this)

  override fun startAnalyzeLeafValues(structure: AbstractTreeStructure, finalRunnable: Runnable) {
    createLeafAnalyzer().startAnalyzeValues(structure, finalRunnable)
  }

  override fun startAnalyzeNullness(structure: AbstractTreeStructure, finalRunnable: Runnable) {
  }

  override fun registerExtraPanelActions(group: DefaultActionGroup, builder: SliceTreeBuilder) {
    if (builder.dataFlowToThis) {
      group.add(GroupByLeavesAction(builder))
    }
  }
}