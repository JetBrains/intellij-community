// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.lang.ASTFactory
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.groovy.GroovyBundle

class ReplaceDotFix(private val oldDot: IElementType, private val newDot: IElementType) : PsiUpdateModCommandQuickFix() {

  private val myName: @IntentionFamilyName String
    get() {
      return GroovyBundle.message("intention.family.name.replace.something.with", oldDot, newDot)
    }

  override fun getFamilyName(): String = myName

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val oldNode = element.node ?: return
    val newNode = ASTFactory.leaf(newDot, newDot.toString())
    CodeEditUtil.setNodeGenerated(newNode, true)
    oldNode.treeParent.replaceChild(oldNode, newNode)
  }
}
