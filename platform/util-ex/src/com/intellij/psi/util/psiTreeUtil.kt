// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil.lastChild
import com.intellij.util.containers.stopAfter
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.*
import kotlin.reflect.KClass

val PsiElement?.elementType: IElementType? get() = PsiUtilCore.getElementType(this)

inline fun <reified T : PsiElement> PsiElement.parentOfType(): T? = parentOfType(T::class)

fun <T : PsiElement> PsiElement.parentOfType(vararg classes: KClass<out T>): T? {
  return PsiTreeUtil.getParentOfType(this, *classes.map { it.java }.toTypedArray())
}


inline fun <reified T : PsiElement> PsiElement.parentsOfType(): Sequence<T> = parentsOfType(T::class.java)

fun <T : PsiElement> PsiElement.parentsOfType(clazz: Class<out T>): Sequence<T> = parents().filterIsInstance(clazz)

fun PsiElement.parents(): Sequence<PsiElement> = generateSequence(this) { it.parent }

fun PsiElement.strictParents(): Sequence<PsiElement> = parents().drop(1)

private typealias ElementAndOffset = Pair<PsiElement, Int>

/**
 * Walks the tree up to (and including) the file level starting from [start].
 *
 * The method doesn't check if [offsetInStart] is within [start].
 *
 * @return returns pairs of (element, offset relative to element)
 */
@Experimental
fun walkUp(start: PsiElement, offsetInStart: Int): Iterator<ElementAndOffset> {
  return iterator {
    elementsAtOffsetUp(start, offsetInStart)
  }
}

/**
 * Walks the tree up to (and including) the file level or [stopAfter] starting from [start].
 *
 * The method doesn't check if [offsetInStart] is within [start].
 * The method doesn't check if [stopAfter] is an actual parent of [start].
 *
 * @return returns pairs of (element, offset relative to element)
 */
@Experimental
fun walkUp(start: PsiElement, offsetInStart: Int, stopAfter: PsiElement): Iterator<ElementAndOffset> {
  return walkUp(start, offsetInStart).stopAfter {
    it.first === stopAfter
  }
}

/**
 * Walks the tree up to (and including) the file level starting from the leaf element at [offsetInFile].
 * If [offsetInFile] is a boundary between two leafs
 * then walks up from each leaf to the common parent of the leafs starting from the rightmost one
 * and continues walking up to the file.
 *
 * **Example**: `foo.bar<caret>[42]`, leaf is `[` element.
 * ```
 *             foo.bar[42]
 *            /           \
 *     foo.bar             [42]
 *    /   |   \           / |  \
 * foo    .    bar       [  42  ]
 * ```
 * Traversal order: `[`, `[42]`, `bar`, `foo.bar`, `foo.bar[42]`
 *
 * @return returns pairs of (element, offset relative to element)
 * @see elementsAtOffsetUp
 */
@Experimental
fun PsiFile.elementsAroundOffsetUp(offsetInFile: Int): Iterator<ElementAndOffset> {
  val leaf = findElementAt(offsetInFile) ?: return Collections.emptyIterator()
  val offsetInLeaf = offsetInFile - leaf.textRange.startOffset
  if (offsetInLeaf == 0) {
    return elementsAroundOffsetUp(leaf)
  }
  else {
    return walkUp(leaf, offsetInLeaf)
  }
}

/**
 * Walks the tree up to (and including) the file level starting from the leaf element at [offsetInFile].
 *
 * @return returns pairs of (element, offset relative to element)
 * @see elementsAroundOffsetUp
 */
@Experimental
fun PsiFile.elementsAtOffsetUp(offsetInFile: Int): Iterator<ElementAndOffset> {
  val leaf = findElementAt(offsetInFile) ?: return Collections.emptyIterator()
  val offsetInLeaf = offsetInFile - leaf.textRange.startOffset
  return walkUp(leaf, offsetInLeaf)
}

private fun elementsAroundOffsetUp(leaf: PsiElement): Iterator<ElementAndOffset> = iterator {
  // We want to still give preference to the elements to the right of the caret,
  // since there may be references/declarations too.
  val leftSubTree = walkUpToCommonParent(leaf) ?: return@iterator
  // At this point elements to the right of the caret (`[` and `[42]`) are processed.
  // The sibling on the left (`foo.bar`) might be a tree,
  // so we should go up from the rightmost leaf of that tree (`bar`).
  val rightMostChild = lastChild(leftSubTree)
  // Since the subtree to the right of the caret was already processed,
  // we don't stop at common parent and just go up to the top.
  elementsAtOffsetUp(rightMostChild, rightMostChild.textLength)
}

private suspend fun SequenceScope<ElementAndOffset>.walkUpToCommonParent(leaf: PsiElement): PsiElement? {
  var current = leaf
  while (true) {
    ProgressManager.checkCanceled()
    yield(ElementAndOffset(current, 0))
    if (current is PsiFile) {
      return null
    }
    current.prevSibling?.let {
      return it
    }
    current = current.parent ?: return null
  }
}

private suspend fun SequenceScope<ElementAndOffset>.elementsAtOffsetUp(element: PsiElement, offsetInElement: Int) {
  var currentElement = element
  var currentOffset = offsetInElement
  while (true) {
    ProgressManager.checkCanceled()
    yield(ElementAndOffset(currentElement, currentOffset))
    if (currentElement is PsiFile) {
      return
    }
    currentOffset += currentElement.startOffsetInParent
    currentElement = currentElement.parent ?: return
  }
}

inline fun <reified T : PsiElement> PsiElement.contextOfType(): T? = contextOfType(T::class)

fun <T : PsiElement> PsiElement.contextOfType(vararg classes: KClass<out T>): T? {
  return PsiTreeUtil.getContextOfType(this, *classes.map { it.java }.toTypedArray())
}

fun PsiElement.siblings(forward: Boolean = true): Sequence<PsiElement> {
  return generateSequence(this) {
    if (forward) {
      it.nextSibling
    }
    else {
      it.prevSibling
    }
  }
}

fun <T : PsiElement> Sequence<T>.skipTokens(tokens: TokenSet): Sequence<T> {
  return filter { it.node.elementType !in tokens }
}
