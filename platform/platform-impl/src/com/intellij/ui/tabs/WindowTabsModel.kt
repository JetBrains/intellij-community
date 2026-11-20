// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs

/**
 * Keeps the order of the frames of [WindowTabsManager], the frames of the tab group, and the current frame.
 * The frames of the tab group are the frames that have a project.
 * The model does not touch Swing. [WindowTabsManager] shows and hides the frames.
 */
internal class WindowTabsModel<T : Any> {
  private val items = ArrayList<T>()
  private val groupItems = HashSet<T>()

  /**
   * The item of the visible frame, or `null` before the first frame shows.
   */
  var current: T? = null
    private set

  /**
   * Returns all items in the order of the tabs. The items outside the tab group are in this list too.
   */
  fun getItems(): List<T> = items

  /**
   * Returns the items of the tab group in the order of the tabs.
   */
  fun getGroupItems(): List<T> = items.filter { it in groupItems }

  fun contains(item: T): Boolean = item in items

  fun isInGroup(item: T): Boolean = item in groupItems

  /**
   * Returns `true` when [item] is in the tab group and the group has one or more other items.
   */
  fun isTabbed(item: T): Boolean = isInGroup(item) && groupItems.size > 1

  /**
   * Adds [item] to the end of the list. The item is not in the tab group.
   */
  fun add(item: T) {
    items.add(item)
  }

  /**
   * Puts [item] in the tab group.
   * Returns `false` when the list does not contain [item], or when [item] is already in the group.
   */
  fun join(item: T): Boolean {
    return contains(item) && groupItems.add(item)
  }

  /**
   * Makes [item] the current item.
   * Returns `false` when the list does not contain [item], or when [item] is already the current item.
   */
  fun select(item: T): Boolean {
    if (item === current || !contains(item)) {
      return false
    }
    current = item
    return true
  }

  /**
   * Removes [item] from the list and from the tab group.
   * When [item] is the current item, the model has no current item after the removal.
   * Then the function returns the item that takes the position of [item], or the item before it.
   * In all other cases, the function returns `null`.
   */
  fun remove(item: T): T? {
    val index = items.indexOf(item)
    if (index == -1) {
      return null
    }
    items.removeAt(index)
    groupItems.remove(item)

    if (current !== item) {
      return null
    }
    current = null
    return items.getOrNull(index) ?: items.getOrNull(index - 1)
  }

  /**
   * Returns the next or the previous item of the tab group after [item], in a circle.
   * Returns `null` when [item] is not in the group, or when the group has fewer than two items.
   */
  fun getAdjacentGroupItem(item: T, next: Boolean): T? {
    val group = getGroupItems()
    val size = group.size
    if (size < 2) {
      return null
    }
    val index = group.indexOf(item)
    if (index == -1) {
      return null
    }
    return group[(index + (if (next) 1 else -1) + size) % size]
  }

  /**
   * Moves [item] to [groupIndex] in the tab group.
   * When [groupIndex] is after the last item of the group, [item] goes to the end of the list.
   */
  fun moveToGroupIndex(item: T, groupIndex: Int) {
    if (!items.remove(item)) {
      return
    }
    val anchor = getGroupItems().getOrNull(groupIndex)
    if (anchor == null) {
      items.add(item)
    }
    else {
      items.add(items.indexOf(anchor), item)
    }
  }
}
