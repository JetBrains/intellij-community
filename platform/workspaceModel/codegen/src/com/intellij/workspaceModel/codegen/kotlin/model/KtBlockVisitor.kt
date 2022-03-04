package org.jetbrains.deft.codegen.patcher

import storage.codegen.patcher.DefType
import storage.codegen.patcher.KtBlock

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
    object: KtBlockVisitor() {
        override fun visitBlock(block: KtBlock, objType: DefType?) = visit(block, objType)
    }.visit(this)
}