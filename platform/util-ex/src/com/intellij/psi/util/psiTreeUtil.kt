// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.util.containers.stopAfter
import org.jetbrains.annotations.ApiStatus
import java.util.*
import kotlin.reflect.KClass

// ----------- Walking children/siblings/parents -------------------------------------------------------------------------------------------

inline fun PsiElement.findParentInFile(withSelf: Boolean = false, predicate: (PsiElement) -> Boolean): PsiElement? {
  var current = when {
    withSelf -> this
    this is PsiFile -> return null
    else -> parent
  }

  while (current != null) {
    if (predicate(current)) return current
    if (current is PsiFile) break
    current = current.parent
  }
  return null
}

inline fun PsiElement.findTopmostParentInFile(withSelf: Boolean = false, predicate: (PsiElement) -> Boolean): PsiElement? {
  var answer: PsiElement? = null
  var current = when {
    withSelf -> this
    this is PsiFile -> return null
    else -> parent
  }

  while (current != null) {
    if (predicate(current)) answer = current
    if (current is PsiFile) break
    current = current.parent
  }
  return answer
}

inline fun <reified T : PsiElement> PsiElement.findParentOfType(strict: Boolean = true): T? {
  return findParentInFile(!strict) { it is T } as? T
}

inline fun <reified T : PsiElement> PsiElement.findTopmostParentOfType(strict: Boolean = true): T? {
  return findTopmostParentInFile(!strict) { it is T } as? T
}

inline fun <reified T : PsiElement> PsiElement.parentOfType(withSelf: Boolean = false): T? {
  return PsiTreeUtil.getParentOfType(this, T::class.java, !withSelf)
}

@Deprecated("Use parentOfTypes()", ReplaceWith("parentOfTypes(*classes)"))
fun <T : PsiElement> PsiElement.parentOfType(vararg classes: KClass<out T>): T? {
  return parentOfTypes(*classes)
}

fun <T : PsiElement> PsiElement.parentOfTypes(vararg classes: KClass<out T>, withSelf: Boolean = false): T? {
  val start = if (withSelf) this else this.parent
  return PsiTreeUtil.getNonStrictParentOfType(start, *classes.map { it.java }.toTypedArray())
}

inline fun <reified T : PsiElement> PsiElement.parentsOfType(withSelf: Boolean = true): Sequence<T> = parentsOfType(T::class.java, withSelf)

@Deprecated("For binary compatibility with older API", level = DeprecationLevel.HIDDEN)
fun <T : PsiElement> PsiElement.parentsOfType(clazz: Class<out T>): Sequence<T> = parentsOfType(clazz, withSelf = true)

fun <T : PsiElement> PsiElement.parentsOfType(clazz: Class<out T>, withSelf: Boolean = true): Sequence<T> {
  return parents(withSelf).filterIsInstance(clazz)
}

/**
 * @param withSelf whether to include [this] element into the sequence
 * @return a sequence of parents, starting with [this] (or parent, depending on [withSelf])
 * and walking up to and including the containing file
 */
fun PsiElement.parents(withSelf: Boolean): Sequence<PsiElement> {
  val seed = if (withSelf) this else parentWithoutWalkingDirectories(this)
  return generateSequence(seed, ::parentWithoutWalkingDirectories)
}

private fun parentWithoutWalkingDirectories(element: PsiElement): PsiElement? {
  return if (element is PsiFile) null else element.parent
}

@ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
@Deprecated("Use PsiElement.parents() function", ReplaceWith("parents(true)"))
fun PsiElement.parents(): Sequence<PsiElement> = parents(true)

@get:ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
@get:Deprecated("Use PsiElement.parents() function", ReplaceWith("parents(true)"))
val PsiElement.parentsWithSelf: Sequence<PsiElement>
  get() = parents(true)

@get:ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
@get:Deprecated("Use PsiElement.parents() function", ReplaceWith("parents(false)"))
val PsiElement.parents: Sequence<PsiElement>
  get() = parents(false)

fun PsiElement.siblings(forward: Boolean = true, withSelf: Boolean = true): Sequence<PsiElement> {
  val seed = when {
    withSelf -> this
    forward -> nextSibling
    else -> prevSibling
  }
  return generateSequence(seed) {
    if (forward) it.nextSibling else it.prevSibling
  }
}

@Deprecated("For binary compatibility with older API", level = DeprecationLevel.HIDDEN)
fun PsiElement.siblings(forward: Boolean = true): Sequence<PsiElement> {
  return siblings(forward)
}

fun PsiElement?.isAncestor(element: PsiElement, strict: Boolean = false): Boolean {
  return PsiTreeUtil.isAncestor(this, element, strict)
}

fun PsiElement.prevLeaf(skipEmptyElements: Boolean = false): PsiElement? = PsiTreeUtil.prevLeaf(this, skipEmptyElements)

fun PsiElement.nextLeaf(skipEmptyElements: Boolean = false): PsiElement? = PsiTreeUtil.nextLeaf(this, skipEmptyElements)

val PsiElement.prevLeafs: Sequence<PsiElement>
  get() = generateSequence({ prevLeaf() }, { it.prevLeaf() })

val PsiElement.nextLeafs: Sequence<PsiElement>
  get() = generateSequence({ nextLeaf() }, { it.nextLeaf() })

fun PsiElement.prevLeaf(filter: (PsiElement) -> Boolean): PsiElement? {
  var leaf = prevLeaf()
  while (leaf != null && !filter(leaf)) {
    leaf = leaf.prevLeaf()
  }
  return leaf
}

fun PsiElement.nextLeaf(filter: (PsiElement) -> Boolean): PsiElement? {
  var leaf = nextLeaf()
  while (leaf != null && !filter(leaf)) {
    leaf = leaf.nextLeaf()
  }
  return leaf
}

// -------------------- Recursive tree visiting --------------------------------------------------------------------------------------------

private val alwaysTrue: (Any?) -> Boolean = { true }

/**
 * @param childrenFirst if `true` then traverse children before parent (postorder),
 *                      if `false` then traverse parent before children (preorder)
 * @param canGoInside a predicate which checks if children of an element should be traversed
 * @return sequence of all children of [this] element (including this element)
 */
fun PsiElement.descendants(
  childrenFirst: Boolean = false,
  canGoInside: (PsiElement) -> Boolean = alwaysTrue
): Sequence<PsiElement> = sequence {
  val root = this@descendants
  if (childrenFirst) {
    visitChildrenAndYield(root, canGoInside)
  }
  else {
    yieldAndVisitChildren(root, canGoInside)
  }
}

private suspend fun SequenceScope<PsiElement>.yieldAndVisitChildren(element: PsiElement, canGoInside: (PsiElement) -> Boolean) {
  yield(element)
  if (canGoInside(element)) {
    var child = element.firstChild
    while (child != null) {
      yieldAndVisitChildren(child, canGoInside)
      child = child.nextSibling
    }
  }
}

private suspend fun SequenceScope<PsiElement>.visitChildrenAndYield(element: PsiElement, canGoInside: (PsiElement) -> Boolean) {
  if (canGoInside(element)) {
    var child = element.firstChild
    while (child != null) {
      visitChildrenAndYield(child, canGoInside)
      child = child.nextSibling
    }
  }
  yield(element)
}

inline fun <reified T : PsiElement> PsiElement.descendantsOfType(childrenFirst: Boolean = false): Sequence<T> {
  return descendants(childrenFirst).filterIsInstance<T>()
}

// -----------------------------------------------------------------------------------------------------------------------------------------

private typealias ElementAndOffset = Pair<PsiElement, Int>

/**
 * Walks the tree up to (and including) the file level starting from [start].
 *
 * The method doesn't check if [offsetInStart] is within [start].
 *
 * @return returns pairs of (element, offset relative to element)
 */
@ApiStatus.Experimental
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
@ApiStatus.Experimental
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
 * @see leavesAroundOffset
 */
@ApiStatus.Experimental
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
@ApiStatus.Experimental
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
  val rightMostChild = PsiTreeUtil.lastChild(leftSubTree)
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

/**
 * @return iterable of elements without children at a given [offsetInFile].
 * Returned elements are the same bottom elements from which walk up is started in [elementsAroundOffsetUp]
 */
@ApiStatus.Experimental
fun PsiFile.leavesAroundOffset(offsetInFile: Int): Iterable<ElementAndOffset> {
  val leaf = findElementAt(offsetInFile) ?: return emptyList()
  val offsetInLeaf = offsetInFile - leaf.textRange.startOffset
  if (offsetInLeaf == 0) {
    val prefLeaf = PsiTreeUtil.prevLeaf(leaf)
    if (prefLeaf != null) {
      return listOf(ElementAndOffset(leaf, 0), ElementAndOffset(prefLeaf, prefLeaf.textLength))
    }
  }
  return listOf(ElementAndOffset(leaf, offsetInLeaf))
}

inline fun <reified T : PsiElement> PsiElement.contextOfType(withSelf: Boolean = false): T? {
  return PsiTreeUtil.getContextOfType(this, T::class.java, !withSelf)
}

fun <T : PsiElement> PsiElement.contextOfType(vararg classes: KClass<out T>): T? {
  return PsiTreeUtil.getContextOfType(this, *classes.map { it.java }.toTypedArray())
}

fun <T : PsiElement> Sequence<T>.skipTokens(tokens: TokenSet): Sequence<T> {
  return filter { it.node.elementType !in tokens }
}

val PsiElement?.elementType: IElementType?
  get() = PsiUtilCore.getElementType(this)

fun PsiFile.hasErrorElementInRange(range: TextRange): Boolean {
  require(range.startOffset >= 0)
  require(range.endOffset <= textLength)

  var leaf = findElementAt(range.startOffset) ?: return false
  var leafRange = leaf.textRange
  if (leafRange.startOffset < range.startOffset) {
    leaf = leaf.nextLeaf(skipEmptyElements = true) ?: return false
    leafRange = leaf.textRange
  }
  check(leafRange.startOffset >= range.startOffset)

  val stopAt = leaf.parents(false).first { range in it.textRange }
  if (stopAt is PsiErrorElement) return true

  if (leafRange.startOffset == range.startOffset) {
    val prevLeaf = leaf.prevLeaf()
    if (prevLeaf is PsiErrorElement && prevLeaf.textLength == 0) return true
  }
  if (leafRange.endOffset == range.endOffset) {
    val nextLeaf = leaf.nextLeaf()
    if (nextLeaf is PsiErrorElement && nextLeaf.textLength == 0) return true
  }

  fun PsiElement.isInsideErrorElement(): Boolean {
    var element: PsiElement? = this
    while (element != null && element != stopAt) {
      if (element is PsiErrorElement) return true
      element = element.parent
    }
    return false
  }

  var endOffset = leafRange.endOffset
  while (endOffset <= range.endOffset) {
    if (leaf.isInsideErrorElement()) return true
    leaf = leaf.nextLeaf() ?: return false
    endOffset += leaf.textLength
  }
  return false
}


inline fun <reified T : PsiElement> PsiElement.childrenOfType(): List<T> = PsiTreeUtil.getChildrenOfTypeAsList(this, T::class.java)
