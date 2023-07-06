// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive

import git4idea.rebase.GitRebaseEntry
import kotlin.math.max
import kotlin.math.min

internal class GitRebaseTodoModel<T : GitRebaseEntry>(initialState: List<Element<T>>) {
  private val rows = ElementList(initialState)

  val elements: List<Element<T>>
    get() = rows.elements

  fun canPick(indices: List<Int>) = canSimpleAction<Type.NonUnite.KeepCommit.Pick>(indices)

  fun pick(indices: List<Int>) {
    keepCommitAction(indices, Type.NonUnite.KeepCommit.Pick)
  }

  fun canEdit(indices: List<Int>) = canSimpleAction<Type.NonUnite.KeepCommit.Edit>(indices)

  fun edit(indices: List<Int>) {
    keepCommitAction(indices, Type.NonUnite.KeepCommit.Edit)
  }

  fun canReword(index: Int): Boolean = rows[index] !is Element.UniteChild

  fun reword(index: Int, message: String) {
    keepCommitAction(listOf(index), Type.NonUnite.KeepCommit.Reword(message))
  }

  fun canDrop(indices: List<Int>) = canSimpleAction<Type.NonUnite.Drop>(indices)

  fun drop(indices: List<Int>) {
    val elements = indices.map { rows[it] }
    elements.filterIsInstance<Element.Simple<T>>().forEach { element ->
      element.type = Type.NonUnite.Drop
    }
    rows.modifyList {
      elements.filterIsInstance<Element.UniteRoot<T>>().forEach { root ->
        convertUniteGroupToSimple(root, Type.NonUnite.Drop)
      }
    }
    // some UniteChildren could be dropped, but indices have not been changed. We should move them away from UniteGroup
    val uniteChildren = indices.sortedDescending().map { rows[it] }.filterIsInstance<Element.UniteChild<T>>()
    rows.modifyList {
      uniteChildren.forEach { child ->
        removeAndMoveUniteChild(child, Type.NonUnite.Drop)
      }
    }
  }

  fun canUnite(indices: List<Int>): Boolean {
    if (indices.size < 2) {
      return false
    }
    val root = when (val element = rows[indices.first()]) {
      is Element.Simple -> return true
      is Element.UniteRoot -> element
      is Element.UniteChild -> element.root
    }
    indices.drop(1).forEach { index ->
      val element = rows[index] as? Element.UniteChild ?: return true
      if (element.root !== root) {
        return true
      }
    }
    return false
  }

  fun unite(indices: List<Int>): Element.UniteRoot<T> {
    lateinit var root: Element.UniteRoot<T>
    rows.modifyList {
      root = convertToRoot(indices.first())
      val newChildren = indices.asSequence().drop(1).map { rows[it] }
        .filter { it !is Element.UniteChild || it.root !== root }
        .map { if (it is Element.UniteRoot) it.uniteGroup else listOf(it) }
        .flatten()
        .distinct()
        .toList()
      moveElements(newChildren, root.newChildIndex())
      newChildren.forEach { newChild ->
        addToUniteGroup(newChild.index, root)
      }
    }
    return root
  }

  fun exchangeIndices(oldIndex: Int, newIndex: Int) {
    rows.modifyList {
      val elementsToMove: List<Element<T>> = when (val element = rows[oldIndex]) {
        is Element.UniteRoot -> element.uniteGroup
        is Element.Simple -> listOf(element)
        is Element.UniteChild -> {
          val newElement = removeAndMoveUniteChild(element, Type.NonUnite.KeepCommit.Pick)
          if (newElement.index == newIndex) {
            // moved to the last position of current UniteGroup
            addToUniteGroup(newElement.index, element.root)
            listOf()
          }
          else {
            listOf(newElement)
          }
        }
      }
      moveElements(elementsToMove, newIndex)
      addToUniteGroupIfNeeded(elementsToMove)
    }
  }

  private fun MutableElementList<T>.addToUniteGroupIfNeeded(elements: List<Element<T>>) {
    if (elements.isEmpty()) {
      return
    }
    val next = getNextElement(elements.last())
    if (next is Element.UniteChild) {
      val newRoot = next.root
      elements.forEach {
        addToUniteGroup(it.index, newRoot)
      }
    }
  }

  private fun MutableElementList<T>.convertUniteGroupToSimple(root: Element.UniteRoot<T>, newType: Type.NonUnite) {
    root.uniteGroup.forEach { element ->
      forceChangeElement(element, Element.Simple(element.index, newType, element.entry))
    }
  }

  private fun MutableElementList<T>.changeUniteChild(child: Element.UniteChild<T>, newElement: Element<T>) {
    val root = child.root
    root.removeChild(child)
    if (root.children.isEmpty()) {
      changeUniteRoot(root, Element.Simple(root.index, root.type, root.entry))
    }
    forceChangeElement(child, newElement)
  }

  private fun MutableElementList<T>.changeUniteRoot(element: Element.UniteRoot<T>, newElement: Element<T>) {
    convertUniteGroupToSimple(element, Type.NonUnite.KeepCommit.Pick)
    forceChangeElement(element, newElement)
  }

  private fun getNextElement(element: Element<T>) = (element.index + 1).takeIf { it < rows.size }?.let { rows[it] }

  private fun MutableElementList<T>.addToUniteGroup(index: Int, root: Element.UniteRoot<T>) {
    val element = elements[index]
    val newChildElement = Element.UniteChild(index, element.entry, root)
    root.addChild(newChildElement)
    when (element) {
      is Element.Simple -> changeSimple(element, newChildElement)
      is Element.UniteRoot -> changeUniteRoot(element, newChildElement)
      is Element.UniteChild -> changeUniteChild(element, newChildElement)
    }
  }

  private fun MutableElementList<T>.convertToRoot(rootIndex: Int): Element.UniteRoot<T> =
    when (val element = rows[rootIndex]) {
      is Element.UniteRoot -> element
      is Element.UniteChild -> element.root
      is Element.Simple -> {
        val currentType = element.type
        val newType = if (currentType is Type.NonUnite.KeepCommit) {
          currentType
        }
        else {
          Type.NonUnite.KeepCommit.Pick
        }
        Element.UniteRoot(rootIndex, newType, element.entry).also { root ->
          changeSimple(element, root)
        }
      }
    }

  private fun keepCommitAction(indices: List<Int>, type: Type.NonUnite.KeepCommit) {
    rows.modifyList {
      indices.sortedDescending().forEach { index ->
        when (val element = rows[index]) {
          is Element.Simple -> {
            element.type = type
          }
          is Element.UniteRoot -> {
            element.type = type
          }
          is Element.UniteChild -> removeAndMoveUniteChild(element, type)
        }
      }
    }
  }

  private fun MutableElementList<T>.removeAndMoveUniteChild(child: Element.UniteChild<T>, newType: Type.NonUnite): Element<T> {
    val root = child.root
    val newIndex = root.lastChildIndex()
    val element = Element.Simple(child.index, newType, child.entry)
    changeUniteChild(child, element)
    moveElements(listOf(element), newIndex)
    return element
  }

  private inline fun <reified T : Type.NonUnite> canSimpleAction(indices: List<Int>): Boolean = indices.map { rows[it] }.any { it.type !is T }

  private class ElementList<T : GitRebaseEntry>(initialState: List<Element<T>>) {
    private val mutableElementList = MutableElementList(initialState)

    val size = initialState.size

    val elements: List<Element<T>>
      get() = mutableElementList.elements

    operator fun get(index: Int): Element<T> = mutableElementList[index]

    fun modifyList(updateFunction: MutableElementList<T>.() -> Unit) {
      mutableElementList.updateFunction()
      validateElements()
    }

    private fun validateElements() {
      elements.forEachIndexed { index, element ->
        check(element.index == index)
        when (element) {
          is Element.UniteRoot -> {
            validateUniteGroup(element)
          }
          is Element.UniteChild -> {
            check(element.root == elements[element.root.index])
          }
          is Element.Simple -> {
          }
        }
      }
    }

    private fun validateUniteGroup(root: Element.UniteRoot<T>) {
      check(root.children.isNotEmpty())
      for (index in root.index + 1 until root.index + root.children.size) {
        val child = elements[index]
        check(child is Element.UniteChild<T>)
        check(child.root == root)
      }
    }
  }

  private class MutableElementList<T : GitRebaseEntry>(initialState: List<Element<T>>) {
    private val _elements: MutableList<Element<T>> = initialState.toMutableList()

    val elements: List<Element<T>>
      get() = _elements

    operator fun get(index: Int): Element<T> = _elements[index]

    fun forceChangeElement(element: Element<T>, newElement: Element<T>) {
      check(element.index == newElement.index)
      _elements[element.index] = newElement
    }

    fun changeSimple(element: Element.Simple<T>, newElement: Element<T>) {
      forceChangeElement(element, newElement)
    }

    fun moveElements(moveGroup: List<Element<T>>, position: Int) {
      if (moveGroup.isEmpty()) {
        return
      }
      val minIndex = min(moveGroup.first().index, position)
      val maxIndex = max(moveGroup.last().index, (position + moveGroup.size).coerceAtMost(_elements.size - 1))

      val moveGroupNewFirstIndex = position.coerceIn(0, _elements.size - moveGroup.size)
      val moveGroupIndices = moveGroup.map { it.index }.toSet()

      val saveElements = (minIndex..maxIndex).filter { it !in moveGroupIndices }.map { _elements[it] }
      val saveElementsBeforeCount = moveGroupNewFirstIndex - minIndex
      val saveElementsBeforeMoveGroup = saveElements.take(saveElementsBeforeCount)
      val saveElementsAfterMoveGroup = saveElements.drop(saveElementsBeforeCount)

      val changedElementsInterval = saveElementsBeforeMoveGroup + moveGroup + saveElementsAfterMoveGroup

      changedElementsInterval.forEachIndexed { i, element ->
        val newIndex = minIndex + i
        _elements[newIndex] = element
        element.index = newIndex
      }

      changedElementsInterval
        .map { element -> if (element is Element.UniteChild) element.root else element }
        .filterIsInstance<Element.UniteRoot<T>>()
        .distinct()
        .forEach { root ->
          root.childrenIndicesChanged()
        }
    }
  }

  sealed class Type(val command: GitRebaseEntry.KnownAction) {
    sealed class NonUnite(command: GitRebaseEntry.KnownAction) : Type(command) {
      sealed class KeepCommit(command: GitRebaseEntry.KnownAction) : NonUnite(command) {
        object Pick : KeepCommit(GitRebaseEntry.Action.PICK)
        object Edit : KeepCommit(GitRebaseEntry.Action.EDIT)
        class Reword(val newMessage: String) : KeepCommit(GitRebaseEntry.Action.REWORD)
      }

      object Drop : NonUnite(GitRebaseEntry.Action.DROP)
    }

    object Unite : Type(GitRebaseEntry.Action.FIXUP)
  }

  sealed class Element<out T : GitRebaseEntry>(var index: Int, open val type: Type, val entry: T) {
    class Simple<T : GitRebaseEntry>(index: Int, override var type: Type.NonUnite, entry: T) : Element<T>(index, type, entry)

    class UniteRoot<T : GitRebaseEntry>(
      index: Int,
      override var type: Type.NonUnite.KeepCommit,
      entry: T
    ) : Element<T>(index, type, entry) {
      private val _children = mutableListOf<UniteChild<T>>()
      val children: List<UniteChild<T>>
        get() = _children

      val uniteGroup
        get() = listOf(this) + _children

      fun addChild(child: UniteChild<T>) {
        check(child.index in this.index + 1..newChildIndex())
        if (child.index == newChildIndex()) {
          _children.add(child)
        }
        else {
          _children.add(child.index - index - 1, child)
        }
      }

      fun lastChildIndex() = _children.lastOrNull()?.index ?: index

      fun newChildIndex() = lastChildIndex() + 1

      fun removeChild(child: UniteChild<T>) {
        _children.remove(child)
      }

      fun childrenIndicesChanged() {
        _children.sortBy { it.index }
      }

      fun getUnitedCommitMessage(singleCommitMessageGetter: (T) -> String): String = uniteGroup.joinToString("\n".repeat(3)) { element ->
        singleCommitMessageGetter(element.entry)
      }
    }

    class UniteChild<T : GitRebaseEntry>(index: Int, entry: T, val root: UniteRoot<T>) : Element<T>(index, Type.Unite, entry)
  }
}