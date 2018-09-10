// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.lang.ASTFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle.message

class ReplaceDotFix(oldDot: IElementType, private val newDot: IElementType) : LocalQuickFix {

  private val myName = message("replace.something.with", oldDot, newDot)

  override fun getFamilyName(): String = myName

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val oldNode = descriptor.psiElement?.node ?: return
    val newNode = ASTFactory.leaf(newDot, newDot.toString())
    CodeEditUtil.setNodeGenerated(newNode, true)
    oldNode.treeParent.replaceChild(oldNode, newNode)
  }
}
