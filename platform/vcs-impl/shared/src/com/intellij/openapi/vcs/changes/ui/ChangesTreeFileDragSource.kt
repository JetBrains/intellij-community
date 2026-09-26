// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ide.dnd.DnDActionInfo
import com.intellij.ide.dnd.DnDDragStartBean
import com.intellij.ide.dnd.DnDImage
import com.intellij.ide.dnd.DnDSupport
import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.Change
import com.intellij.platform.vcs.changes.ChangesUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Makes the tree a drag source that offers its selected files, and nothing else. The tree does not become
 * a drop target. Use this for a read-only tree, such as the changes tree of the Log view. A tree that also
 * accepts a drop uses [ChangesTreeDnDSupport] instead.
 */
@ApiStatus.Internal
fun ChangesTree.installFileDragSource(disposable: Disposable) {
  val tree = this
  DnDSupport.createBuilder(tree)
    .disableAsTarget()
    .setBeanProvider { info -> createFileDragStartBean(tree, info) }
    .setImageProvider { info -> createFileDragImage(tree, info) }
    .setDisposableParent(disposable)
    .install()
}

private fun createFileDragStartBean(tree: ChangesTree, info: DnDActionInfo): DnDDragStartBean? {
  if (!info.isMove) return null
  val paths = selectedFilePaths(tree)
  if (paths.isEmpty()) return null
  return DnDDragStartBean(ChangesTreeFileDragBean(paths))
}

private fun createFileDragImage(tree: ChangesTree, info: DnDActionInfo): DnDImage? {
  if (createFileDragStartBean(tree, info) == null) return null
  val count = ChangesTreeDnDSupport.getSelectionCount(tree)
  return ChangesTreeDnDSupport.createDragImage(tree, VcsBundle.message("vcs.dnd.image.text.n.files", count))
}

/**
 * The after path is the location of the change on disk. A deletion has no after path, and the before path
 * still names the file, so [ChangesUtil.getFilePath] keeps a deletion in the drag.
 *
 * A selected directory row contributes every change under it, because [VcsTreeModelData.selected] walks the subtree.
 */
private fun selectedFilePaths(tree: ChangesTree): List<FilePath> {
  return VcsTreeModelData.selected(tree)
    .iterateUserObjects(Change::class.java)
    .map { change -> ChangesUtil.getFilePath(change) }
    .toList()
}
