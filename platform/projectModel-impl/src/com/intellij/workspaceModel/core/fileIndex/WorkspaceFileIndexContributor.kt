// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus

/**
 * Implement this interface and register the implementation as `com.intellij.workspaceModel.fileIndexContributor` extension in plugin.xml 
 * file to specify which files mentioned in WorkSpace Model entities should be considered as part of the workspace.
 *
 * [WorkspaceFileIndex] can be used to access data collected from the contributors. 
 */
@ApiStatus.OverrideOnly
interface WorkspaceFileIndexContributor<E : WorkspaceEntity> {
  /**
   * Specifies interface of entities processed by this contributor.
   */
  val entityClass: Class<E>

  /**
   * Implement this function and call functions from [registrar] to specify files and directories which should be included or excluded from
   * the workspace. 
   * 
   * The implementation may use properties from [entity] or from its parents only and don't use other data which may change.
   * If properties from other entities are used for computation, their classes must be registered in [dependenciesOnOtherEntities].
   * This is necessary to ensure that [WorkspaceFileIndex] is properly updated when entities change. 
   * 
   * This function is currently called synchronously under Write Action, so its implementation should run very fast.
   */
  fun registerFileSets(entity: E, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage)

  /**
   * Describes other entities which properties may be used in [registerFileSets].
   */
  val dependenciesOnOtherEntities: List<DependencyDescription<E>>
    get() = emptyList() 
}

sealed interface DependencyDescription<E : WorkspaceEntity> {
  /**
   * Indicates that the contributor must be called for child entities when any property in parent entity of type [P] changes. 
   */
  data class OnParent<E : WorkspaceEntity, P : WorkspaceEntity>(
    /** Type of parent entity */
    val parentClass: Class<P>,
    /** Computes child entities by the parent */
    val childrenGetter: (P) -> Sequence<E>
  ) : DependencyDescription<E>

  /**
   * Indicates that the contributor must be called for the parent entity when any child of type [C] is added, removed or replaced.
   */
  data class OnChild<E : WorkspaceEntity, C : WorkspaceEntity>(
    /** Type of child entity */
    val childClass: Class<C>,
    /** Computes parent entity by the child */
    val parentGetter: (C) -> E
  ) : DependencyDescription<E>
}

/**
 * Describes possible kinds of files and directories in the workspace.
 */
enum class WorkspaceFileKind {
  /**
   * Describes files which are supposed to be edited in the IDE as part of the workspace. 
   * Files of this kind constitute 'Project Files' scope in UI. 
   * This kind corresponds to [com.intellij.openapi.roots.FileIndex.isInContent] method in the old API.
   */
  CONTENT,

  /**
   * Subset of [CONTENT] which is used to identify test files. 
   * Files of this kind constitute 'Project Test Files' scope in UI.
   * This kind corresponds to [com.intellij.openapi.roots.FileIndex.isInTestSourceContent] method in the old API.
   */
  TEST_CONTENT,

  /**
   * Describes files which may be referenced by [CONTENT] files, but aren't supposed to be edited in the IDE. 
   * Often they are in some binary format, though it is not necessary.
   * Files of this kind together with [CONTENT] files constitute 'Project and Libraries' scope in UI.
   * This kind corresponds to [com.intellij.openapi.roots.ProjectFileIndex.isInLibrary] method in the old API. 
   */
  EXTERNAL,

  /**
   * Describes files containing source code of [EXTERNAL] files. They aren't supposed to be edited in the IDE and aren't directly
   * referenced from [CONTENT] files. This kind was introduced mainly for compatibility with the old code, it corresponds to
   * [com.intellij.openapi.roots.ProjectFileIndex.isInLibrarySource] method. 
   */
  EXTERNAL_SOURCE
}

/**
 * Provides functions which can be used to specify which files should be included or excluded from the workspace. 
 * This interface may be used only inside implementation of [WorkspaceFileIndexContributor.registerFileSets] function.
 */
interface WorkspaceFileSetRegistrar {
  /**
   * Includes [root] and all files under it to the workspace.
   * Specific files or directories under [root] (or even [root] itself) may be excluded from the workspace by [registerExcludedRoot],
   * [registerExclusionPatterns] and [registerExclusionCondition] functions.
   * @param kind specify kind which will be assigned to the files
   * @param entity first parameter of [WorkspaceFileIndexContributor.registerFileSets] must be passed here
   * @param customData optional custom data which will be associated with the root and can be accessed via [WorkspaceFileSetWithCustomData].
   */
  fun registerFileSet(root: VirtualFileUrl,
                      kind: WorkspaceFileKind,
                      entity: WorkspaceEntity,
                      customData: WorkspaceFileSetData?)

  /**
   * A variant of [registerFileSet] function which takes [VirtualFile] instead of [VirtualFileUrl]. 
   * This function is considered as a temporary solution until all contributors to [WorkspaceFileIndex] are migrated to Workspace Model. 
   */
  fun registerFileSet(root: VirtualFile,
                      kind: WorkspaceFileKind,
                      entity: WorkspaceEntity,
                      customData: WorkspaceFileSetData?)

  /**
   * Excludes [excludedRoot] and all files under it from the workspace. 
   * Specific files or directories under [excludedRoot] may be included back by [registerFileSet].
   * @param entity first parameter of [WorkspaceFileIndexContributor.registerFileSets] must be passed here
   */
  fun registerExcludedRoot(excludedRoot: VirtualFileUrl, entity: WorkspaceEntity)

  /**
   * Excludes [excludedRoot] and all files under it from [excludedFrom] kind of files. 
   * This is a temporary solution to keep behavior of old code. 
   */
  fun registerExcludedRoot(excludedRoot: VirtualFile, excludedFrom: WorkspaceFileKind, entity: WorkspaceEntity)

  /**
   * Excludes all files and directories under [root] which names match to one of [patterns] (`*` and `?` wildcards are supported) from the
   * workspace.
   * @param entity first parameter of [WorkspaceFileIndexContributor.registerFileSets] must be passed here
   */
  fun registerExclusionPatterns(root: VirtualFileUrl, patterns: List<String>, entity: WorkspaceEntity)

  /**
   * Excludes all files and directories under [root] which satisfy [condition] from the workspace.
   * @param condition may access the passed file and its parents and children only
   * @param entity first parameter of [WorkspaceFileIndexContributor.registerFileSets] must be passed here
   */
  fun registerExclusionCondition(root: VirtualFile, condition: (VirtualFile) -> Boolean, entity: WorkspaceEntity)
}
