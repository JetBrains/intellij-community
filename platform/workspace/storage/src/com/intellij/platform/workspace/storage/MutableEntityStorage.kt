// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * Writeable interface to storage. 
 * Use it if you need to build a storage from scratch or modify an existing storage in a way which requires reading its state after 
 * modifications.
 * 
 * In order to modify entities inside the IDE process, use functions from [WorkspaceModel][com.intellij.platform.backend.workspace.WorkspaceModel]
 * interface.
 * 
 * Instances of this interface are not thread safe.
 *
 * ## Adding, modifying and removing entities
 * 
 * In order to add a new entity to the storage, create it by calling the companion object of its interface and then pass it to [addEntity]
 * function:
 * ```kotlin
 * //mandatory properties are passed as parameters
 * val module = ModuleEntity(moduleName, dependencies, entitySource) { 
 *   //optional properties can be initialized in the lambda passed as the last parameter
 *   type = ModuleTypeId.JAVA_MODULE
 * }
 * ...
 * WorkspaceModel.getInstance(project).updateProjectModel("Add module") { builder ->
 *   builder.addEntity(module)
 * }  
 * ```
 * You may first prepare a whole tree of entities, and then add the root one to the storage, [addEntity] will automatically add all the
 * children.
 * 
 * In order to modify or remove an entity, you first need to find its instance in this instance of [MutableEntityStorage]. You can do this
 * by [resolving][SymbolicEntityId.resolve] its [SymbolicEntityId], or by [resolving][EntityReference.resolve] an [EntityReference], or
 * iterating by children of another entity:
 * ```
 * WorkspaceModel.getInstance(project).updateProjectModel("Update module") { builder ->
 *   val module = ModuleId(moduleName).resolve(builder) ?: ...
 *   val groupPath = module.groupPath ?: ...
 *   builder.removeEntity(groupPath)
 *   //a special extension function 'modifyEntity' is generated for each entity type
 *   builder.modifyEntity(module) {
 *     name = prefix + name
 *   }
 * }
 * ```
 *
 * ## Adding and removing child entities
 * 
 * There are two equivalent ways to add a child entity to an existing parent entity:
 * * specify parent when creating the child, and add the child via [addEntity]: 
 * ```
 * val contentRoot = ContentRootEntity(url, emptyList(), entitySource) {
 *   this.module = module
 * }
 * builder.addEntity(contentRoot)
 * ```
 * * call [modifyEntity] on the parent entity and modify its property to include the new child:
 * ```
 * val contentRoot = ContentRootEntity(url, emptyList(), entitySource)
 * builder.modifyEntity(module) {
 *   this.contentRoots = this.contentRoots + contentRoot
 * }
 * ```
 * 
 * In order to remove a child entity, it's enough to call [removeEntity] for it, the reference in its parent will be update automatically.
 * Also, if the reference to the parent is declared as non-null in the child interface, it's enough to modify reference to the children
 * in the parent entity:
 * ```
 * builder.modifyEntity(module) {
 *   contentRoots = contentRoots.filter { it.url != contentUrlToRemove }
 * }
 * ```
 * If you do that for a child with nullable reference to the parent, the child will be detached from the parent but won't be removed from
 * the storage.
 * 
 * ## Batch operations
 * Besides operation with individual entities, [MutableEntityStorage] supports two batch operations: [addDiff] and [replaceBySource].
 * 
 * ### Add Diff
 * Each instance of [MutableEntityStorage] records changes made in it: addition, modification and removal of entities. Such changes made
 * in one instance may be applied to a different instance by calling [addDiff] function. This can be used to run tasks which fill the
 * storage in parallel. For example, when configuration of a project is read from *.iml files, the IDE creates a separate empty 
 * [MutableEntityStorage] for each file, and run tasks which parse an *.iml file and load entities from it to the corresponding storage 
 * concurrently. When the tasks finish, their results are merged into the single storage via [addDiff].
 * 
 * ### Replace by source
 * 
 * Usually, entities in the workspace model storage are created from data loaded from some configuration files. When these configuration 
 * files change, we need to update the corresponding entities in the storage. It would be rather hard and error-prone to analyze what
 * exactly was changed in the files since the previous version, so we use a different approach. Each entity must specify its 
 * [source][WorkspaceEntity.entitySource] describing where the entity comes from. For example, for entities loaded from configuration files
 * from .idea directory, [entitySource][WorkspaceEntity.entitySource] points to the corresponding xml file. When IDE detects changes in some
 * configuration files, it loads new entities from the created and modified files to a separate [MutableEntityStorage] instance, and then
 * calls [replaceBySource] passing a filter which accepts entity sources corresponding to the all created, modified and deleted files and
 * the prepared [MutableEntityStorage] instance. This operation actualizes the part of the storage affected by the change trying to minimize
 * the number of changes in the entities.
 */
interface MutableEntityStorage : EntityStorage {
  /**
   * Returns `true` if there are changes recorded in this storage after its creation. Note, that this method may return `true` if these
   * changes actually don't modify the resulting set of entities, you may use [hasSameEntities] to perform more sophisticated check.
   */
  fun hasChanges(): Boolean

  /**
   * Add the given entity to the storage. All children of the entity will also be added.
   */
  infix fun <T : WorkspaceEntity> addEntity(entity: T): T

  /**
   * Modifies the given entity [e] by passing a builder interface for it to [change]. 
   * This function isn't supposed to be used directly, it's more convenient to use a specialized `modifyEntity` extension function which
   * is generated for each entity type.
   */
  fun <M : WorkspaceEntity.Builder<out T>, T : WorkspaceEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T

  /**
   * Remove the entity from the storage if it's present. All child entities of the entity with non-null reference to the parent entity are
   * also removed.
   * @return `true` if the entity was removed, `false` if the entity was not in the storage
   */
  fun removeEntity(e: WorkspaceEntity): Boolean

  /**
   * Finds all entities which [entitySource][WorkspaceEntity.entitySource] satisfy the given [sourceFilter] and replaces them with the 
   * entities from [replaceWith]. 
   * This operation tries to minimize the number of changes in the storage and matches entities from [replaceWith] with existing entities:
   * * entities with [symbolicId][WorkspaceEntityWithSymbolicId.symbolicId] are matched by its value;
   * * entities with properties annotated with [@EqualsBy][EqualsBy] are matched by values of all such properties;
   * * other entities are matched by values of all properties excluding [entitySource][WorkspaceEntity.entitySource].
   * 
   * If there is a matching entity, new values of properties are compared with the old ones and [EntityChange.Replaced] event is recorded 
   * if they are changed. 
   * Children of the existing entity which [entitySource][WorkspaceEntity.entitySource] doesn't satisfy [sourceFilter] are kept, other 
   * children are replaced by the children of the new entity.
   * 
   * The exact list of optimizations is an implementation detail.
   */
  fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: EntityStorage)

  /**
   * Return changes in entities recorded in this instance. [original] parameter is used to get the old instances of modified
   * and removed entities.
   * 
   * This function isn't supported to be used by client code directly. In order to subscribe to changes in entities inside the IDE process,
   * use [WorkspaceModelTopics][com.intellij.platform.backend.workspace.WorkspaceModelTopics].
   *
   * # Behavior details
   *
   * The [EntityChange.Added] and [EntityChange.Removed] events are straightforward and generated in case of added or removed entities.
   *
   * The [EntityChange.Replaced] is generated in case if any of the fields of the entity changes the value in the newer
   *   version of storage.
   * This means that this event is generated in two cases: "primitive" field change (Int, String, data class, etc.) or
   *   changes of the references to other entities. The change of references may happen interectly by modifying the referred entity.
   *   For example, if we remove child entity, we'll generate two events: remove for child and replace for parent.
   *                if we add a new child entity, we'll also generate two events: add for child and replace for parent.
   *
   * # Examples
   *
   * Assuming the following structure of entities: A --> B --> C
   * Where A is the root entity and B and C are the children.
   *
   * - If we modify the primitive field of C: [Replace(C)]
   * - If we remove C: [Replace(B), Remove(C)]
   * - If we remove reference between B and C: [Replace(B), Replace(C)]
   * - If we remove B: [Replace(A), Remove(B), Remove(C)] - C is cascade removed
   *
   * Another example:
   * Before: A --> B  C, After A  C --> B
   * We have an entity `A` that has a child `B` and we move this child from `A` to `C`
   *
   * Produced events: [Replace(A), Replace(B), Replace(C)]
   *
   */
  @ApiStatus.Internal
  fun collectChanges(original: EntityStorage): Map<Class<*>, List<EntityChange<*>>>

  /**
   * Merges changes from [diff] to this storage. 
   * It's supposed that [diff] was created either via [MutableEntityStorage.create] function, or via [MutableEntityStorage.from] with the 
   * same base storage as this one. 
   * Calling the function for a mutable storage with a different base storage may lead to unpredictable results.
   */
  fun addDiff(diff: MutableEntityStorage)

  /**
   * Returns `true` if this instance contains entities with the same properties as [original] storage it was created from. 
   * The difference from [hasChanges] is that this method will return `true` in cases when an entity was removed, and then a new entity
   * with the same properties was added.
   */
  fun hasSameEntities(original: EntityStorage): Boolean

  /**
   * Returns an existing or create a new mapping with the given [identifier].
   * By convention, identifier should be a dot-separated string prepended with the product name, e.g.
   * * intellij.modules.bridge
   * * intellij.facets.bridge
   * * rider.backend.id
   */
  fun <T> getMutableExternalMapping(identifier: @NonNls String): MutableExternalEntityMapping<T>
  
  /**
   * Returns a number which is incremented after each change in the storage.
   */
  val modificationCount: Long

  companion object {
    /**
     * Creates an empty mutable storage. It may be populated with new entities and passed to [addDiff] or [replaceBySource].  
     */
    @JvmStatic
    fun create(): MutableEntityStorage = MutableEntityStorageImpl.create()

    /**
     * Creates a mutable copy of the given [storage].
     */
    @JvmStatic
    fun from(storage: EntityStorage): MutableEntityStorage = MutableEntityStorageImpl.from(storage)
  }
}

/**
 * Describes a change in an entity. Instances of this class are obtained from [VersionedStorageChange].
 */
sealed class EntityChange<T : WorkspaceEntity> {
  /**
   * Returns the entity which was removed or replaced in the change.
   */
  abstract val oldEntity: T?

  /**
   * Returns the entity which was added or used as a replacement in the change.
   */
  abstract val newEntity: T?

  /**
   * Describes an entity which was added to the storage, directly (via [MutableEntityStorage.addEntity]) or indirectly (as a child of another
   * added entity, or as a result of a batch operation ([replaceBySource][MutableEntityStorage.replaceBySource], 
   * [addDiff][MutableEntityStorage.addDiff]), or after modification of a reference from a parent entity).
   */
  data class Added<T : WorkspaceEntity>(val entity: T) : EntityChange<T>() {
    override val oldEntity: T?
      get() = null
    override val newEntity: T
      get() = entity
  }

  /**
   * Describes an entity which was removed from the storage, directly (via [MutableEntityStorage.removeEntity]) or indirectly (as a child of 
   * another removed entity, or as a result of a batch operation ([replaceBySource][MutableEntityStorage.replaceBySource],
   * [addDiff][MutableEntityStorage.addDiff]), or after modification of a reference from a parent entity).
   */
  data class Removed<T : WorkspaceEntity>(val entity: T) : EntityChange<T>() {
    override val oldEntity: T
      get() = entity
    override val newEntity: T?
      get() = null
  }

  /**
   * Describes changes in properties of an entity.
   * Old values of the properties can be obtained via [oldEntity], new values can be obtained via [newEntity].
   */
  data class Replaced<T : WorkspaceEntity>(override val oldEntity: T, override val newEntity: T) : EntityChange<T>()
}

/**
 * Marks a property as a key field for [replaceBySource][MutableEntityStorage.replaceBySource] operation.
 * Entities will be compared based on properties with this annotation.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
annotation class EqualsBy
