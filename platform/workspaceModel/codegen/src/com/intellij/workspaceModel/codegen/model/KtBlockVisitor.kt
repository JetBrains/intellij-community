// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.model

open class KtBlockVisitor {
  open fun visit(block: KtBlock) {
    block.children.forEach {
      visit(it)
    }

    val objType = block.scope?.ktInterface?.objType
    visitBlock(block, objType)
  }

  open fun visitBlock(block: KtBlock, objType: DefType?) = Unit
}

fun KtBlock.visitRecursively(visit: (block: KtBlock, objType: DefType?) -> Unit) {
  object : KtBlockVisitor() {
    override fun visitBlock(block: KtBlock, objType: DefType?) = visit(block, objType)
  }.visit(this)
}