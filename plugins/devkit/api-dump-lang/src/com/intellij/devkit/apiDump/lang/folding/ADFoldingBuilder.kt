// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.folding

import com.intellij.devkit.apiDump.lang.psi.ADClassDeclaration
import com.intellij.devkit.apiDump.lang.psi.ADVisitor
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement

internal class ADFoldingBuilder : FoldingBuilderEx() {
  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<out FoldingDescriptor> {
    val descriptors = mutableListOf<FoldingDescriptor>()
    root.acceptChildren(object: ADVisitor() {
      override fun visitClassDeclaration(o: ADClassDeclaration) {
        val descriptor = FoldingDescriptor(o.node, o.textRange, null, o.classHeader.typeReference.text)
        descriptors.add(descriptor)
      }
    })
    return descriptors.toTypedArray()
  }

  override fun isCollapsedByDefault(node: ASTNode): Boolean = false

  override fun getPlaceholderText(node: ASTNode): String? = null
}