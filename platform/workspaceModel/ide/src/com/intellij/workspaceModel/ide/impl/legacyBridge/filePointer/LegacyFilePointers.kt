// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer

import com.google.common.collect.ArrayListMultimap
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.*
import kotlin.reflect.KClass

/**
 * Container for all legacy file pointers to track the files and update the state of workspace model regarding to it
 * All legacy file pointers are collected in a single container to perform project model update in a single change
 */
class LegacyModelRootsFilePointers(val project: Project) {
  private val virtualFileManager = VirtualFileUrlManager.getInstance(project)
  internal var isInsideFilePointersUpdate = false
    private set

  private val pointers = listOf(
    // Library roots
    TypedEntityFileWatcher(
      LibraryEntity::class, ModifiableLibraryEntity::class, containerToUrl = { it.url.url },
      urlToContainer = { oldContainer, newUrl ->
        LibraryRoot(virtualFileManager.fromUrl(newUrl), oldContainer.type, oldContainer.inclusionOptions)
      },
      containerListGetter = { roots }, modificator = { oldRoot, newRoot ->
      roots = roots - oldRoot
      roots = roots + newRoot
    }
    ),
    // Library excluded roots
    TypedEntityWithVfuFileWatcher(
      LibraryEntity::class, ModifiableLibraryEntity::class, containerListGetter = { excludedRoots },
      modificator = { oldVirtualFileUrl, newVirtualFileUrl ->
        excludedRoots = excludedRoots - oldVirtualFileUrl
        excludedRoots = excludedRoots + newVirtualFileUrl
      },
      virtualFileManager = virtualFileManager
    ),
    // Content root urls
    TypedEntityWithVfuFileWatcher(
      ContentRootEntity::class, ModifiableContentRootEntity::class, containerGetter = { url },
      modificator = { _, newVirtualFileUrl -> url = newVirtualFileUrl },
      virtualFileManager = virtualFileManager
    ),
    // Content root excluded urls
    TypedEntityWithVfuFileWatcher(
      ContentRootEntity::class, ModifiableContentRootEntity::class, containerListGetter = { excludedUrls },
      modificator = { oldVirtualFileUrl, newVirtualFileUrl ->
        excludedUrls = excludedUrls - oldVirtualFileUrl
        excludedUrls = excludedUrls + newVirtualFileUrl
      },
      virtualFileManager = virtualFileManager
    ),
    // Source roots
    TypedEntityWithVfuFileWatcher(
      SourceRootEntity::class, ModifiableSourceRootEntity::class, containerGetter = { url },
      modificator = { _, newVirtualFileUrl -> url = newVirtualFileUrl },
      virtualFileManager = virtualFileManager
    ),
    // Java module settings entity compiler output
    TypedEntityWithVfuFileWatcher(
      JavaModuleSettingsEntity::class, ModifiableJavaModuleSettingsEntity::class, containerGetter = { compilerOutput },
      modificator = { _, newVirtualFileUrl -> compilerOutput = newVirtualFileUrl },
      virtualFileManager = virtualFileManager
    ),
    // Java module settings entity compiler output for tests
    TypedEntityWithVfuFileWatcher(
      JavaModuleSettingsEntity::class, ModifiableJavaModuleSettingsEntity::class, containerGetter = { compilerOutputForTests },
      modificator = { _, newVirtualFileUrl -> compilerOutputForTests = newVirtualFileUrl },
      virtualFileManager = virtualFileManager
    ),
    EntitySourceFileWatcher(JpsFileEntitySource.ExactFile::class, { it.file.url }, { source, file -> source.copy(file = file) },
                            virtualFileManager),
    EntitySourceFileWatcher(JpsFileEntitySource.FileInDirectory::class, { it.directory.url },
                            { source, file -> source.copy(directory = file) }, virtualFileManager)
  )

  fun onVfsChange(oldUrl: String, newUrl: String) {
    try {
      isInsideFilePointersUpdate = true
      WorkspaceModel.getInstance(project).updateProjectModel { diff ->
        pointers.forEach { it.onVfsChange(oldUrl, newUrl, diff) }
      }
    }
    finally {
      isInsideFilePointersUpdate = false
    }
  }

  fun onModelChange(newStorage: WorkspaceEntityStorage) {
    pointers.filterIsInstance<TypedEntityFileWatcher<*, *, *>>().forEach { it.onModelChange(newStorage) }
  }

  fun clear() {
    pointers.filterIsInstance<TypedEntityFileWatcher<*, *, *>>().forEach { it.clear() }
  }
}

private interface LegacyFileWatcher<E : WorkspaceEntity> {
  fun onVfsChange(oldUrl: String, newUrl: String, diff: WorkspaceEntityStorageBuilder)
}

private class EntitySourceFileWatcher<T : EntitySource>(
  val entitySource: KClass<T>,
  val containerToUrl: (T) -> String,
  val createNewSource: (T, VirtualFileUrl) -> T,
  val virtualFileManager: VirtualFileUrlManager
) : LegacyFileWatcher<WorkspaceEntity> {
  override fun onVfsChange(oldUrl: String, newUrl: String, diff: WorkspaceEntityStorageBuilder) {
    val entities = diff.entitiesBySource { it::class == entitySource }
    for ((entitySource, mapOfEntities) in entities) {
      @Suppress("UNCHECKED_CAST")
      val urlFromContainer = containerToUrl(entitySource as T)
      if (!FileUtil.startsWith(urlFromContainer, oldUrl)) continue

      val newVfurl = virtualFileManager.fromUrl(newUrl + urlFromContainer.substring(oldUrl.length))
      val newEntitySource = createNewSource(entitySource, newVfurl)

      mapOfEntities.values.flatten().forEach { diff.changeSource(it, newEntitySource) }
    }
  }
}

/**
 * Legacy file pointer with a [VirtualFileUrl] as a container for url (see the docs for [TypedEntityFileWatcher])
 *
 * [containerGetter] - function on how to extract the container from the entity
 * [containerListGetter] - function on how to extract from the entity a list of containers
 * There 2 functions are created for better convenience. You should use only one from them.
 */
private class TypedEntityWithVfuFileWatcher<E : WorkspaceEntity, M : ModifiableWorkspaceEntity<E>>(
  entityClass: KClass<E>,
  modifiableEntityClass: KClass<M>,
  containerGetter: E.() -> VirtualFileUrl? = { null },
  containerListGetter: E.() -> List<VirtualFileUrl> = { this.containerGetter()?.let { listOf(it) } ?: listOf() },
  modificator: M.(VirtualFileUrl, VirtualFileUrl) -> Unit,
  val virtualFileManager: VirtualFileUrlManager
) : TypedEntityFileWatcher<VirtualFileUrl, E, M>(
  entityClass, modifiableEntityClass,
  { it.url }, { _, newUrl -> virtualFileManager.fromUrl(newUrl) },
  containerListGetter, modificator
)

/**
 * Legacy file pointer that can track and update urls stored in a [WorkspaceEntity].
 * [entityClass] - class of a [WorkspaceEntity] that contains an url being tracked
 * [modifiableEntityClass] - class of modifiable entity of [entityClass]
 * [containerToUrl] - function to extract the url from the container
 * [urlToContainer] - function on how to build a container from the url and the previous version of the container
 * [containerListGetter] - function on how to extract from the entity a list of containers
 * [modificator] - function for modifying an entity
 *
 * A "container for the url" is a class where the url is stored. In most cases it's a just [VirtualFileUrl], but sometimes it can be a more
 *   complicated structure like LibraryEntity -> roots (LibraryRoot) -> url (VirtualFileUrl).
 *     See a LegacyFilePointer for LibraryEntity.roots.url
 */
private open class TypedEntityFileWatcher<T, E : WorkspaceEntity, M : ModifiableWorkspaceEntity<E>>(
  val entityClass: KClass<E>,
  val modifiableEntityClass: KClass<M>,
  val containerToUrl: (T) -> String,
  val urlToContainer: (T, String) -> T,
  val containerListGetter: E.() -> List<T>,
  val modificator: M.(T, T) -> Unit
) : LegacyFileWatcher<E> {
  // A multimap the associates the "url container" to the typed entity
  private val savedContainers = ArrayListMultimap.create<T, E>()

  override fun onVfsChange(oldUrl: String, newUrl: String, diff: WorkspaceEntityStorageBuilder) {
    val toAdd = mutableListOf<Pair<T, E>>()
    val toRemove = mutableListOf<Pair<T, E>>()

    savedContainers.forEach { existingUrlContainer, entity ->
      val savedUrl = containerToUrl(existingUrlContainer)   // Get the url as a String
      if (FileUtil.startsWith(savedUrl, oldUrl)) {     // Check if the tracked url contains the updated file
        toRemove.add(existingUrlContainer to entity)

        // Take newUrl as a base and add the rest of the path
        // So, if we rename `/root/myPath` to `/root/myNewPath`, the [savedUrl] would be `/root/myPath/contentFile` and
        //   the [newTrackedUrl] - `/root/myNewPath/contentFile`
        val newTrackedUrl = newUrl + savedUrl.substring(oldUrl.length)

        val newContainer = urlToContainer(existingUrlContainer, newTrackedUrl)
        val modifiedEntity = diff.modifyEntity(modifiableEntityClass.java, entity) {
          this.modificator(existingUrlContainer, newContainer)
        }
        toAdd.add(newContainer to modifiedEntity)
      }
    }
    toRemove.forEach { savedContainers.remove(it.first, it.second) }
    toAdd.forEach { savedContainers.put(it.first, it.second) }
  }

  fun onModelChange(newStorage: WorkspaceEntityStorage) {
    savedContainers.clear()
    newStorage.entities(entityClass.java).forEach {
      for (container in it.containerListGetter()) {
        savedContainers.put(container, it)
      }
    }
  }

  fun clear() = savedContainers.clear()
}
