// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.libraries.libraries

import com.google.common.collect.ArrayListMultimap
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.JpsFileEntitySource
import com.intellij.workspace.ide.WorkspaceModel
import kotlin.reflect.KClass

/**
 * Container for all legacy file pointers to track the files and update the state of workspace model regarding to it
 * All legacy file pointers are collected in a single container to perform project model update in a single change
 */
class LegacyModelRootsFilePointers(val project: Project) {
  private val pointers = listOf(
    // Library roots
    TypedEntityFileWatcher(
      LibraryEntity::class, ModifiableLibraryEntity::class, containerToUrl = { it.url.url },
      urlToContainer = { oldContainer, newUrl ->
        LibraryRoot(VirtualFileUrlManager.fromUrl(newUrl), oldContainer.type, oldContainer.inclusionOptions)
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
      }
    ),
    // Content root urls
    TypedEntityWithVfuFileWatcher(
      ContentRootEntity::class, ModifiableContentRootEntity::class, containerGetter = { url },
      modificator = { _, newVirtualFileUrl -> url = newVirtualFileUrl }
    ),
    // Content root excluded urls
    TypedEntityWithVfuFileWatcher(
      ContentRootEntity::class, ModifiableContentRootEntity::class, containerListGetter = { excludedUrls },
      modificator = { oldVirtualFileUrl, newVirtualFileUrl ->
        excludedUrls = excludedUrls - oldVirtualFileUrl
        excludedUrls = excludedUrls + newVirtualFileUrl
      }
    ),
    // Source roots
    TypedEntityWithVfuFileWatcher(
      SourceRootEntity::class, ModifiableSourceRootEntity::class, containerGetter = { url },
      modificator = { _, newVirtualFileUrl -> url = newVirtualFileUrl }
    ),
    // Java module settings entity compiler output
    TypedEntityWithVfuFileWatcher(
      JavaModuleSettingsEntity::class, ModifiableJavaModuleSettingsEntity::class, containerGetter = { compilerOutput },
      modificator = { _, newVirtualFileUrl -> compilerOutput = newVirtualFileUrl }
    ),
    // Java module settings entity compiler output for tests
    TypedEntityWithVfuFileWatcher(
      JavaModuleSettingsEntity::class, ModifiableJavaModuleSettingsEntity::class, containerGetter = { compilerOutputForTests },
      modificator = { _, newVirtualFileUrl -> compilerOutputForTests = newVirtualFileUrl }
    ),
    EntitySourceFileWatcher(JpsFileEntitySource.ExactFile::class, { it.file.url }, { source, file -> source.copy(file = file) }),
    EntitySourceFileWatcher(JpsFileEntitySource.FileInDirectory::class, { it.directory.url },
                            { source, file -> source.copy(directory = file) })
  )

  fun onVfsChange(oldUrl: String, newUrl: String) {
    // Here the workspace model updates its state without notification to the message bus
    //   because in the original implementation moving of roots doesn't fire any events.
    WorkspaceModel.getInstance(project).updateProjectModelSilent { diff ->
      for (pointer in pointers) {
        val updateChain: Map<out TypedEntity, TypedEntity> = pointer.onVfsChange(oldUrl, newUrl, diff)
        if (pointer is TypedEntityFileWatcher<*, *, *>) {
          pointers.filterIsInstance<TypedEntityFileWatcher<*, *, *>>().forEach {
            // Update stored entities to the last snapshot
            if (it.entityClass == pointer.entityClass) {
              it.update(updateChain)
            }
          }
        }
      }
    }
  }

  fun onModelChange(newStorage: TypedEntityStorage) {
    pointers.filterIsInstance<TypedEntityFileWatcher<*, *, *>>().forEach { it.onModelChange(newStorage) }
  }

  fun clear() {
    pointers.filterIsInstance<TypedEntityFileWatcher<*, *, *>>().forEach { it.clear() }
  }
}

private interface LegacyFileWatcher<E : TypedEntity> {
  fun onVfsChange(oldUrl: String, newUrl: String, diff: TypedEntityStorageBuilder): Map<E, E>
}

private class EntitySourceFileWatcher<T : EntitySource>(
  val entitySource: KClass<T>,
  val containerToUrl: (T) -> String,
  val createNewSource: (T, VirtualFileUrl) -> T
) : LegacyFileWatcher<TypedEntity> {
  override fun onVfsChange(oldUrl: String, newUrl: String, diff: TypedEntityStorageBuilder): Map<TypedEntity, TypedEntity> {
    val entities = diff.entitiesBySource { it::class == entitySource }
    for ((entitySource, mapOfEntities) in entities) {
      @Suppress("UNCHECKED_CAST")
      val urlFromContainer = containerToUrl(entitySource as T)
      if (!FileUtil.startsWith(urlFromContainer, oldUrl)) continue

      val newVfurl = VirtualFileUrlManager.fromUrl(newUrl + urlFromContainer.substring(oldUrl.length))
      val newEntitySource = createNewSource(entitySource, newVfurl)

      mapOfEntities.values.flatten().forEach { diff.changeSource(it, newEntitySource) }
    }
    return emptyMap()
  }
}

/**
 * Legacy file pointer with a [VirtualFileUrl] as a container for url (see the docs for [TypedEntityFileWatcher])
 *
 * [containerGetter] - function on how to extract the container from the entity
 * [containerListGetter] - function on how to extract from the entity a list of containers
 * There 2 functions are created for better convenience. You should use only one from them.
 */
private class TypedEntityWithVfuFileWatcher<E : TypedEntity, M : ModifiableTypedEntity<E>>(
  entityClass: KClass<E>,
  modifiableEntityClass: KClass<M>,
  containerGetter: E.() -> VirtualFileUrl? = { null },
  containerListGetter: E.() -> List<VirtualFileUrl> = { this.containerGetter()?.let { listOf(it) } ?: listOf() },
  modificator: M.(VirtualFileUrl, VirtualFileUrl) -> Unit
) : TypedEntityFileWatcher<VirtualFileUrl, E, M>(
  entityClass, modifiableEntityClass,
  { it.url }, { _, newUrl -> VirtualFileUrlManager.fromUrl(newUrl) },
  containerListGetter, modificator
)

/**
 * Legacy file pointer that can track and update urls stored in a [TypedEntity].
 * [entityClass] - class of a [TypedEntity] that contains an url being tracked
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
private open class TypedEntityFileWatcher<T, E : TypedEntity, M : ModifiableTypedEntity<E>>(
  val entityClass: KClass<E>,
  val modifiableEntityClass: KClass<M>,
  val containerToUrl: (T) -> String,
  val urlToContainer: (T, String) -> T,
  val containerListGetter: E.() -> List<T>,
  val modificator: M.(T, T) -> Unit
) : LegacyFileWatcher<E> {
  // A multimap the associates the "url container" to the typed entity
  private val savedContainers = ArrayListMultimap.create<T, E>()

  override fun onVfsChange(oldUrl: String, newUrl: String, diff: TypedEntityStorageBuilder): Map<E, E> {
    val toAdd = mutableListOf<Pair<T, E>>()
    val toRemove = mutableListOf<Pair<T, E>>()

    // The updateChain is a map "TypedEntity to TypedEntity". It contains the association of the entity before the update
    //   to the updated version. E.g.: let's say we're updating `MyEntity v.1` and we'll get `MyEntity v.2`. We should put these two
    //   entities to the map (`MyEntity v.1` -> `MyEntity v.2`). So, when the next time (e.g. on the next iteration) `MyEntity` should be
    //   updated, we search in the map the latest version of `MyEntity` and work with it. After the next update we put the updated version
    //   back to the map (`MyEntity v.1` -> `MyEntity v.3`).
    // This chain is created because in case [savedContainers] contains two instances of `MyEntity`, the second one won't be updated after
    //   the first update. Of course, we can search in the EntityStore for the latest version, but it can take some time since not every
    //   TypedEntity has an id.
    val updateChain = mutableMapOf<E, E>()

    savedContainers.forEach { existingUrlContainer, entity ->
      val entityCurrentVersion: E = updateChain[entity] ?: entity
      val savedUrl = containerToUrl(existingUrlContainer)   // Get the url as a String
      if (FileUtil.startsWith(savedUrl, oldUrl)) {     // Check if the tracked url contains the updated file
        toRemove.add(existingUrlContainer to entity)

        // Take newUrl as a base and add the rest of the path
        // So, if we rename `/root/myPath` to `/root/myNewPath`, the [savedUrl] would be `/root/myPath/contentFile` and
        //   the [newTrackedUrl] - `/root/myNewPath/contentFile`
        val newTrackedUrl = newUrl + savedUrl.substring(oldUrl.length)

        val newContainer = urlToContainer(existingUrlContainer, newTrackedUrl)
        val modifiedEntity = diff.modifyEntity(modifiableEntityClass.java, entityCurrentVersion) {
          this.modificator(existingUrlContainer, newContainer)
        }
        updateChain[entity] = modifiedEntity
        toAdd.add(newContainer to modifiedEntity)
      }
    }
    toRemove.forEach { savedContainers.remove(it.first, it.second) }
    toAdd.forEach { savedContainers.put(it.first, it.second) }
    return updateChain
  }

  fun onModelChange(newStorage: TypedEntityStorage) {
    savedContainers.clear()
    newStorage.entities(entityClass.java).forEach {
      for (container in it.containerListGetter()) {
        savedContainers.put(container, it)
      }
    }
  }

  /** See the comments in [onVfsChange] for the information about updateChain */
  fun update(chain: Map<*, *>) {
    val toAdd = mutableListOf<Pair<T, E>>()
    val toRemove = mutableListOf<Pair<T, E>>()
    savedContainers.forEach { key, value ->
      @Suppress("UNCHECKED_CAST")
      val updatedEntity: E? = chain[value] as? E? ?: return@forEach
      if (updatedEntity != null) {
        toRemove.add(key to value)
        toAdd.add(key to updatedEntity)
      }
    }
    toRemove.forEach { savedContainers.remove(it.first, it.second) }
    toAdd.forEach { savedContainers.put(it.first, it.second) }
  }

  fun clear() = savedContainers.clear()
}
