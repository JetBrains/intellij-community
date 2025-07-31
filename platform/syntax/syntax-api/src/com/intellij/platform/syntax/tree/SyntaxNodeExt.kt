// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file: JvmName("SyntaxNodeExt")
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.tree

import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.JvmName

val SyntaxNode.length: Int get() = endOffset - startOffset

fun SyntaxNode.children(): Sequence<SyntaxNode> =
  generateSequence({ firstChild() }) { child -> child.nextSibling() }

fun SyntaxNode.childrenBackward(): Sequence<SyntaxNode> =
  generateSequence({ lastChild() }) { child -> child.prevSibling() }

tailrec fun SyntaxNode.skip(): SyntaxNode? =
  nextSibling() ?: parent()?.skip()

fun SyntaxNode.next(): SyntaxNode? = firstChild() ?: skip()

private tailrec fun SyntaxNode.findFirstSiblingForward(cond: (SyntaxNode) -> Boolean): SyntaxNode? =
  when {
    cond(this) -> this
    else -> nextSibling()?.findFirstSiblingForward(cond)
  }

private tailrec fun SyntaxNode.findFirstSiblingBackward(cond: (SyntaxNode) -> Boolean): SyntaxNode? =
  when {
    cond(this) -> this
    else -> prevSibling()?.findFirstSiblingBackward(cond)
  }

fun SyntaxNode.findFirstChild(cond: (SyntaxNode) -> Boolean): SyntaxNode? =
  firstChild()?.findFirstSiblingForward(cond)

fun SyntaxNode.findFirstChild(type: Any): SyntaxNode? =
  findFirstChild { it.type == type }

fun SyntaxNode.findLastChild(cond: (SyntaxNode) -> Boolean): SyntaxNode? =
  lastChild()?.findFirstSiblingBackward(cond)

fun SyntaxNode.findLastChild(type: Any): SyntaxNode? =
  findLastChild { it.type == type }

fun SyntaxNode.pp(indent: String = ""): String =
  indent + type + (if (firstChild() == null) ": (${text.toString().replace("\n", "\\n")})" else "") + "\n" + generateSequence(
    firstChild()) { it.nextSibling() }.map { it.pp("$indent  ") }.joinToString("")

fun SyntaxNode.descendants(filtering: (SyntaxNode) -> Boolean = { true }): Sequence<SyntaxNode> =
  sequenceOf(this) + if (filtering(this)) children().flatMap { treeWalker -> treeWalker.descendants(filtering) } else emptySequence()

fun SyntaxNode.descendantsReversed(filtering: (SyntaxNode) -> Boolean = { true }): Sequence<SyntaxNode> =
  if (filtering(this)) {
    generateSequence(lastChild()) { it.prevSibling() }.flatMap { treeWalker -> treeWalker.descendantsReversed(filtering) }
  }
  else {
    emptySequence()
  } + sequenceOf(this)

fun SyntaxNode.ancestors(excludeSelf: Boolean = false): Sequence<SyntaxNode> =
  generateSequence(if (excludeSelf) parent() else this) { w -> w.parent() }

fun SyntaxNode.siblings(forward: Boolean = true, excludeSelf: Boolean = false): Sequence<SyntaxNode> =
  generateSequence(if (excludeSelf) if (forward) nextSibling() else prevSibling() else this) { w ->
    if (forward) w.nextSibling() else w.prevSibling()
  }

private fun SyntaxNode.skipRight(): SyntaxNode? = skip()

private tailrec fun SyntaxNode.skipLeft(): SyntaxNode? =
  prevSibling() ?: parent()?.skipLeft()

fun SyntaxNode.firstLeaf(): SyntaxNode? {
  tailrec fun SyntaxNode.loop(): SyntaxNode =
    when (val c = firstChild()) {
      null -> this
      else -> c.loop()
    }
  return firstChild()?.loop()
}

fun SyntaxNode.leafLeft(): SyntaxNode? {
  tailrec fun SyntaxNode.loop(): SyntaxNode? =
    when (val lc = lastChild()) {
      null -> this
      else -> lc.loop()
    }
  return this.skipLeft()?.loop()
}

fun SyntaxNode.sequenceLeft(excludeSelf: Boolean = false): Sequence<SyntaxNode> =
  generateSequence(if (excludeSelf) leafLeft() else this) { it.leafLeft() }

fun SyntaxNode.leafRight(): SyntaxNode? =
  generateSequence(this.skipRight()) { it.firstChild() }.lastOrNull()

fun SyntaxNode.sequenceRight(excludeSelf: Boolean = false): Sequence<SyntaxNode> =
  generateSequence(if (excludeSelf) leafRight() else this) { it.leafRight() }

fun SyntaxNode.lastLeaf(): SyntaxNode? {
  tailrec fun SyntaxNode.loop(): SyntaxNode? =
    when (val lc = lastChild()) {
      null -> this
      else -> lc.loop()
    }
  return lastChild()?.loop()
}

fun SyntaxNode.leafByOffset(offset: Int): SyntaxNode? =
  if (offset >= startOffset && offset < endOffset) descendantsByOffset(offset).last()
  else null

fun SyntaxNode.descendantsByOffset(offset: Int): Sequence<SyntaxNode> =
  generateSequence(this) { it.childByOffset(offset) }

tailrec fun <T> SyntaxNode.ancestorWithType(t: T): SyntaxNode? =
  when {
    this.type == t -> this
    else -> parent()?.ancestorWithType(t)
  }

fun SyntaxNode.skipForward(type: Any, excludeSelf: Boolean = false): SyntaxNode? {
  return sequenceRight(excludeSelf).filterNot { type == it.type }.firstOrNull()
}