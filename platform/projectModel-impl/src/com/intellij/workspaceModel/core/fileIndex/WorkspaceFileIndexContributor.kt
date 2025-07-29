// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus

/**
 * Implement this interface and register the implementation as `com.intellij.workspaceModel.fileIndexContributor` extension in plugin.xml 
 * file to specify which files and directories mentioned in WorkSpace Model entities should be considered as part of the workspace.
 *
 * [WorkspaceFileIndex] can be used to access data collected from the contributors.
 * See [the package documentation](psi_element://com.intellij.workspaceModel.core.fileIndex) for more details.
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
   * The implementation may use properties from [entity] or from its parents or its children only and don't use other data which may change.
   * If properties from other entities are used for computation, their classes must be registered in [dependenciesOnOtherEntities].
   * This is necessary to ensure that [WorkspaceFileIndex] is properly updated when entities change. 
   * 
   * This function is currently called synchronously under Write Action, so its implementation should run very fast.
   */
  fun registerFileSets(entity: E, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage)

  /**
   * Describes other entities whose properties may be used in [registerFileSets].
   *
   * The [WorkspaceFileIndexContributor] is registered per-entity, however if the implementation of the contributor accesses properties
   * that refer to other entities, the changes of the referred entities won't be tracked by the contributor automatically.
   *
   * For example, if the contributor for ParentEntity accesses the ChildEntity,
   * the ChildEntity should be listed in [dependenciesOnOtherEntities]:
   * ```
   * class MyParentContributor : WorkspaceFileIndexContributor<ParentEntity> {
   *
   *   override fun registerFileSets(entity: ParentEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
   *     val childUrl = entity.child.url   // <--- Accessing fields from the referred entity
   *     registrar.registerFileSet(childUrl, ...)
   *   }
   *
   *   override val dependenciesOnOtherEntities = listOf(DependencyDescription.OnChild(ChildEntity::class.java) { it.parent })
   * }
   *```
   * Then MyParentContributor with overridden [dependenciesOnOtherEntities] will be called when ChildEntity specified
   * as a dependency is changed.
   */
  val dependenciesOnOtherEntities: List<DependencyDescription<E>>
    get() = emptyList()

  /**
   * Override this property and return [EntityStorageKind.UNLOADED] from it to indicate that the contributor should work on the entities
   * from the unloaded storage. This is rarely needed because entities from the unloaded storage should be ignored in most of the cases. 
   */
  val storageKind: EntityStorageKind
    get() = EntityStorageKind.MAIN
}

enum class EntityStorageKind {
  /** Main storage of entities, accessible via [com.intellij.workspaceModel.ide.WorkspaceModel.entityStorage] */
  MAIN,
  /** Storage for unloaded entities, accessible via [com.intellij.workspaceModel.ide.WorkspaceModel.currentSnapshotOfUnloadedEntities] */
  UNLOADED
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

  /**
   * Indicates that the contributor must be called for the entities [R] when any entity of type [E] is added, removed or replaced.
   */
  data class OnEntity<R : WorkspaceEntity, E : WorkspaceEntity>(
    /** Type of entity */
    val entityClass: Class<E>,
    /** Type of entity [R] which has a dependency on entity [E] */
    val resultClass: Class<R>,
    /** Computes entities*/
    val resultGetter: (E) -> Sequence<R>
  ) : DependencyDescription<R>

  /**
   * Indicates that the contributor must be called for the entities [R] when any entity of type [E] adds the first
   * or remove the last reference to [R].
   */
  @ApiStatus.Experimental
  data class OnReference<R: WorkspaceEntityWithSymbolicId, E: WorkspaceEntityWithSymbolicId>(
    /** Type that could contain references to [R] */
    val referenceHolderClass: Class<E>,
    /** Type for which a contributor should be called */
    val resultClass: Class<R>,
    /** Computes references */
    val referencedEntitiesGetter: (E) -> Sequence<SymbolicEntityId<R>>
  ): DependencyDescription<R>
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
   * Describe files that are in the workspace but should not be indexed. These files are usually in a directory that the user has opened,
   * but before the project is imported by any build system. They can be edited.
   */
  CONTENT_NON_INDEXABLE,

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
  EXTERNAL_SOURCE,

  /**
   * Describes files which may be referenced by [CONTENT], [EXTERNAL], or [EXTERNAL_SOURCE] files,
   * and aren't supposed to be edited in the IDE.
   * The main difference between this kind and [EXTERNAL] is that these files are way more exotic, and shouldn't be included
   * in 'Project and Libraries' scope in UI, but rather added to customized resolve scopes of certain elements, and `All` scope.
   * Files of this kind ![com.intellij.openapi.roots.ProjectFileIndex.isInProject].
   *
   * This kind corresponds to files from [com.intellij.util.indexing.IndexableSetContributor] in the old API.
   */
  CUSTOM;
  
  val isContent: Boolean
    get() = this == CONTENT || this == TEST_CONTENT || this == CONTENT_NON_INDEXABLE

  val isIndexable: Boolean
    get() = (this != CONTENT_NON_INDEXABLE)
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
  @ApiStatus.Obsolete
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
  fun registerExcludedRoot(excludedRoot: VirtualFileUrl, excludedFrom: WorkspaceFileKind, entity: WorkspaceEntity)

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
  fun registerExclusionCondition(root: VirtualFileUrl, condition: (VirtualFile) -> Boolean, entity: WorkspaceEntity)

  /**
   * Includes [file] to the workspace. Note, that unlike the default [registerFileSet], files under [file] won't be included.
   * @param kind specify kind which will be assigned to the files
   * @param entity first parameter of [WorkspaceFileIndexContributor.registerFileSets] must be passed here
   * @param customData optional custom data which will be associated with the root and can be accessed via [WorkspaceFileSetWithCustomData].
   */
  fun registerNonRecursiveFileSet(
    file: VirtualFileUrl,
    kind: WorkspaceFileKind,
    entity: WorkspaceEntity,
    customData: WorkspaceFileSetData?,
  )
}
