// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf.tree

import com.intellij.openapi.util.Key
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import org.jetbrains.annotations.ApiStatus

/**
 * Since we delete our entities when replacing the root, we can't use them for restoring the selection.
 * Therefore, we now need to save the data required for restoring the selection to `DataContext`.
 */
@ApiStatus.Internal
sealed interface SelectionIdentifier {
  fun shouldBeSelected(item: EntityChangesBrowserNode<*>): Boolean
}

@ApiStatus.Internal
class ChangelistSelectionIdentifier(oldChangelist: ShelvedChangeListEntity) : SelectionIdentifier {
  private val name = oldChangelist.name
  private val date = oldChangelist.date

  override fun shouldBeSelected(item: EntityChangesBrowserNode<*>): Boolean {
    val changeListEntity = item.userObject as? ShelvedChangeListEntity ?: return false
    return name == changeListEntity.name && date == changeListEntity.date
  }
}

@ApiStatus.Internal
class ChangeSelectionIdentifier(oldChange: ShelvedChangeEntity) : SelectionIdentifier {
  private val filePath = oldChange.filePath

  override fun shouldBeSelected(item: EntityChangesBrowserNode<*>): Boolean {
    val change = item.userObject as? ShelvedChangeEntity ?: return false
    return filePath == change.filePath
  }
}

@ApiStatus.Internal
val SELECTION_IDENTIFIER_KEY: Key<SelectionIdentifier> = Key<SelectionIdentifier>("ChangesTree.SelectionIdentifier")