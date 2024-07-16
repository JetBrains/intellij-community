// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update

import com.intellij.openapi.vcs.VcsBundle
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Container for files which have been affected by an update/integrate/status operation.
 * The files are grouped by file status.
 *
 * @see UpdateEnvironment.fillGroups
 *
 * @see UpdateEnvironment.updateDirectories
 */
class UpdatedFiles private constructor() {
  private val groups = ArrayList<FileGroup>()

  fun registerGroup(fileGroup: FileGroup): FileGroup {
    val existing = getGroupById(fileGroup.id)
    if (existing != null) {
      return existing
    }

    groups.add(fileGroup)
    return fileGroup
  }

  val isEmpty: Boolean
    get() = groups.all { it.isEmpty }

  fun getGroupById(id: String?): FileGroup? = if (id == null) null else findByIdIn(groups, id)

  val topLevelGroups: List<FileGroup>
    get() = groups

  override fun toString(): String = groups.toString()

  companion object {
    @Internal
    fun readExternal(element: Element): UpdatedFiles {
      val result = create()
      FileGroup.readGroupsFromElement(result.groups, element)
      return result
    }

    @JvmStatic
    fun create(): UpdatedFiles {
      val result = UpdatedFiles()
      val updatedFromServer = result.registerGroup(
        FileGroup(VcsBundle.message("update.group.name.updated.from.server"), VcsBundle.message("status.group.name.changed.on.server"),
                  false, FileGroup.CHANGED_ON_SERVER_ID, false))

      updatedFromServer.addChild(
        FileGroup(VcsBundle.message("update.group.name.updated"), VcsBundle.message("status.group.name.changed"), false,
                  FileGroup.UPDATED_ID, false))
      updatedFromServer.addChild(
        FileGroup(VcsBundle.message("update.group.name.created"), VcsBundle.message("status.group.name.created"), false,
                  FileGroup.CREATED_ID, false))
      updatedFromServer.addChild(
        FileGroup(VcsBundle.message("update.group.name.deleted"), VcsBundle.message("status.group.name.deleted"), false,
                  FileGroup.REMOVED_FROM_REPOSITORY_ID, true))
      updatedFromServer.addChild(
        FileGroup(VcsBundle.message("update.group.name.restored"), VcsBundle.message("status.group.name.will.be.restored"), false,
                  FileGroup.RESTORED_ID, false))

      result.registerGroup(
        FileGroup(VcsBundle.message("update.group.name.modified"), VcsBundle.message("status.group.name.modified"), false,
                  FileGroup.MODIFIED_ID, false))
      result.registerGroup(
        FileGroup(VcsBundle.message("update.group.name.skipped"), VcsBundle.message("status.group.name.skipped"), false,
                  FileGroup.SKIPPED_ID, false))

      result.registerGroup(FileGroup(VcsBundle.message("update.group.name.merged.with.conflicts"),
                                     VcsBundle.message("status.group.name.will.be.merged.with.conflicts"), false,
                                     FileGroup.MERGED_WITH_CONFLICT_ID, false))
      result.registerGroup(FileGroup(VcsBundle.message("update.group.name.merged.with.tree.conflicts"),
                                     VcsBundle.message("update.group.name.merged.with.tree.conflicts"), false,
                                     FileGroup.MERGED_WITH_TREE_CONFLICT, false))
      result.registerGroup(FileGroup(VcsBundle.message("update.group.name.merged.with.property.conflicts"),
                                     VcsBundle.message("status.group.name.will.be.merged.with.property.conflicts"),
                                     false, FileGroup.MERGED_WITH_PROPERTY_CONFLICT_ID, false))
      result.registerGroup(
        FileGroup(VcsBundle.message("update.group.name.merged"), VcsBundle.message("status.group.name.will.be.merged"), false,
                  FileGroup.MERGED_ID, false))
      result.registerGroup(
        FileGroup(VcsBundle.message("update.group.name.not.in.repository"), VcsBundle.message("status.group.name.not.in.repository"), true,
                  FileGroup.UNKNOWN_ID, false))
      result.registerGroup(
        FileGroup(VcsBundle.message("update.group.name.locally.added"), VcsBundle.message("status.group.name.locally.added"), false,
                  FileGroup.LOCALLY_ADDED_ID, false))
      result.registerGroup(
        FileGroup(VcsBundle.message("update.group.name.locally.removed"), VcsBundle.message("status.group.name.locally.removed"), false,
                  FileGroup.LOCALLY_REMOVED_ID, false))
      result.registerGroup(
        FileGroup(VcsBundle.message("update.group.name.switched"), VcsBundle.message("status.group.name.switched"), false,
                  FileGroup.SWITCHED_ID, false))
      return result
    }
  }
}

private fun findByIdIn(groups: List<FileGroup>, id: String): FileGroup? {
  for (fileGroup in groups) {
    if (id == fileGroup.id) {
      return fileGroup
    }

    val foundInChildren = findByIdIn(fileGroup.children, id)
    if (foundInChildren != null) {
      return foundInChildren
    }
  }
  return null
}
