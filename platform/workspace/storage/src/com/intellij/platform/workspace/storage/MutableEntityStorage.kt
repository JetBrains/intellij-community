// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.workspace.storage.impl.ImmutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.currentStackTrace
import org.jetbrains.annotations.ApiStatus

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
 * by [resolving][SymbolicEntityId.resolve] its [SymbolicEntityId], or by [resolving][EntityPointer.resolve] an [EntityPointer], or
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
 * See the documentation for [modifyEntity] for more examples on when the children are automatically removed.
 * 
 * ## Batch operations
 * Besides operation with individual entities, [MutableEntityStorage] supports two batch operations: [applyChangesFrom] and [replaceBySource].
 * 
 * ### Apply Changes From
 * Each instance of [MutableEntityStorage] records changes made in it: addition, modification and removal of entities. Such changes made
 * in one instance may be applied to a different instance by calling [applyChangesFrom] function.
 *
 * **Use cases**:
 *
 * - **Parallel filling of the storage.**
 *
 *   Example: When configuration of a project is read from `*.iml` files, the IDE creates a separate empty [MutableEntityStorage] for each
 *   file, and run tasks which parse an `*.iml` file and load entities from it to the corresponding storage concurrently.
 *   When the tasks finish, their results are merged into the single storage via [applyChangesFrom].
 *
 * - **Accumulating changes.**
 *
 *   Example: This fits the case the user performs modifications in the "Project Structure" dialog. We don't want the changes to be
 *   applied immediately, so we accumulate changes in a builder and then add them to the main storage using the [applyChangesFrom] command when
 *   the user presses "apply" button.
 *
 * This is not a full list of use cases. You can use this operation based on your needs.
 * 
 * ### Replace by source
 *
 * Partially replace the storage with the new one based on the predicate.
 * This operation actualizes the part of the storage affected by the change trying to minimize the number of changes in the entities.
 *
 * **Rationale:**
 *
 * Usually, entities in the workspace model storage are created from data loaded from some configuration files. When these configuration
 * files change, we need to update the corresponding entities in the storage. It would be rather hard and error-prone to analyze what
 * exactly was changed in the files since the previous version, so we use a different approach. Each entity must specify its 
 * [source][WorkspaceEntity.entitySource] describing where the entity comes from.
 *
 * **Use cases**:
 *
 * - **Importing a project model from the external system.**
 *
 *   Example: In a maven project, we can create the project model each time we detect the `pom.xml` was changed. We apply the new project
 *   model to the main storage using the [replaceBySource] operation. This will affect only changed entities that were created from maven.
 *
 * - **Working with JPS build system**
 *
 *   Example: For entities loaded from configuration files from .idea directory, [entitySource][WorkspaceEntity.entitySource] points
 *   to the corresponding xml file. When IDE detects changes in some configuration files, it loads new entities from the created
 *   and modified files to a separate [MutableEntityStorage] instance, and then calls [replaceBySource] passing a filter which accepts
 *   entity sources corresponding to the all created, modified and deleted files and the prepared [MutableEntityStorage] instance.
 *
 * This is not a full list of use cases. You can use this operation based on your needs.
 */
public interface MutableEntityStorage : EntityStorage {
  /**
   * Add the given entity to the storage. All children of the entity will also be added.
   *
   * If any of the children exists in a different storage, this child will be copied with all sub-children and added to the storage.
   * If any of the children exists in `this` storage, the reference to this child will be added instead of creating a new child entity.
   */
  public infix fun <M: WorkspaceEntity.Builder<T>, T : WorkspaceEntity> addEntity(entity: M): T

  /**
   * Modifies the given entity [e] by passing a builder interface for it to [change]. 
   * This function isn't supposed to be used directly, it's more convenient to use a specialized `modifySomethingEntity` extension function
   *   which is generated for each entity type.
   *
   * # Side effects on other entities
   *
   * The modification of the entity can have side effects on other related entities. For example, some children entities
   *  may be removed by modification the parent. The entity storage tries to preserve the child entities unless this breaks the consistency.
   *
   *
   * ## One To Many
   * In One-To-Many connection, if the parent is modified and some existing child is not presented in the new list of children,
   *   the child will be removed in case the parent field is not nullable. If the parent field in child is nullable, the child remains
   *   in the storage and returns `null` when accessing the parent.
   * ```
   * interface ChildEntity : WorkspaceEntity {
   *   val parent: ParentEntity
   * }
   *
   * ...
   *
   * val child = ChildEntity(..)
   * val parent = ParentEntity(...) {
   *   this.children = listOf(child)
   * }
   *
   * builder.modifyParentEntity(parent) {
   *   this.children = emptyList()
   * }
   *
   * // child entity is removed because parent field is not nullable
   * ```
   * ```
   * interface ChildEntity : WorkspaceEntity {
   *   val parent: ParentEntity?
   * }
   *
   * ...
   *
   * val child = ChildEntity(..)
   * val parent = ParentEntity(...) {
   *   this.children = listOf(child)
   * }
   *
   * builder.modifyParentEntity(parent) {
   *   this.children = emptyList()
   * }
   *
   * // child entity is NOT removed because parent field is nullable
   * ```
   *
   * ## One To One
   * In One-To-One connection, if the child of a parent is set to null or another child, the previous child is removed in case
   *   the parent field is not nullable. If the child has a nullable parent, the child remains in the storage and returns `null` when
   *   accessing the parent.
   * ```
   * interface ChildEntity : WorkspaceEntity {
   *   val parent: ParentEntity
   * }
   *
   * ...
   *
   * val child = ChildEntity(..)
   * val parent = ParentEntity(...) {
   *   this.child = child
   * }
   *
   * builder.modifyParentEntity(parent) {
   *   this.child = null
   * }
   *
   * // child entity is removed because parent field is not nullable
   * ```
   * ```
   * interface ChildEntity : WorkspaceEntity {
   *   val parent: ParentEntity?
   * }
   *
   * ...
   *
   * val child = ChildEntity(..)
   * val parent = ParentEntity(...) {
   *   this.child = child
   * }
   *
   * builder.modifyParentEntity(parent) {
   *   this.child = null
   * }
   *
   * // child entity is NOT removed because parent field is nullable
   * ```
   *
   * @return updated entity [e]. There is no guarantee if the modifications are visible or not in the instance of [e],
   *   so [e] should not be used after the modification
   */
  public fun <M : WorkspaceEntity.Builder<out T>, T : WorkspaceEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T

  /**
   * Remove the entity from the storage if it's present.
   *
   * All children of the entity will be cascade removed.
   * If the child entities are not supposed to be removed, they should be modified by setting `null` to the parent of the
   *   child entities. This can be done only for children with a nullable parent.
   *
   * @return `true` if the entity was removed, `false` if the entity was not in the storage
   */
  public fun removeEntity(e: WorkspaceEntity): Boolean

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
  public fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: EntityStorage)

  /**
   * Merges changes from [builder] to this storage.
   * It's supposed that [builder] was created either via [MutableEntityStorage.create] function, or via [MutableEntityStorage.from] with the
   * same base storage as this one. 
   * Calling the function for a mutable storage with a different base storage may lead to unpredictable results.
   */
  public fun applyChangesFrom(builder: MutableEntityStorage)

  /**
   * Returns an existing or create a new mapping with the given [identifier].
   */
  public fun <T> getMutableExternalMapping(identifier: ExternalMappingKey<T>): MutableExternalEntityMapping<T>

  /**
   * Returns a snapshot of the current state. It won't be affected by future changes.
   */
  public fun toSnapshot(): ImmutableEntityStorage

  public companion object {
    private val LOG = logger<MutableEntityStorage>()
    /**
     * Creates an empty mutable storage. It may be populated with new entities and passed to [applyChangesFrom] or [replaceBySource].
     */
    @JvmStatic
    public fun create(): MutableEntityStorage = from(ImmutableEntityStorage.empty())

    /**
     * Creates a mutable copy of the given [storage] snapshot.
     */
    @JvmStatic
    public fun from(storage: ImmutableEntityStorage): MutableEntityStorage {
      storage as ImmutableEntityStorageImpl
      val newBuilder = MutableEntityStorageImpl(originalSnapshot = storage)
      LOG.trace { "Create new builder $newBuilder from $storage.\n${currentStackTrace(10)}" }
      return newBuilder
    }
  }
}

/**
 * Describes a change in an entity. Instances of this class are obtained from [VersionedStorageChange].
 */
public sealed class EntityChange<T : WorkspaceEntity> {
  /**
   * Returns the entity which was removed or replaced in the change.
   */
  public abstract val oldEntity: T?

  /**
   * Returns the entity which was added or used as a replacement in the change.
   */
  public abstract val newEntity: T?

  /**
   * Describes an entity which was added to the storage, directly (via [MutableEntityStorage.addEntity]) or indirectly (as a child of another
   * added entity, or as a result of a batch operation ([replaceBySource][MutableEntityStorage.replaceBySource], 
   * [applyChangesFrom][MutableEntityStorage.applyChangesFrom]), or after modification of a reference from a parent entity).
   */
  public data class Added<T : WorkspaceEntity>(private val entity: T) : EntityChange<T>() {
    override val oldEntity: T?
      get() = null
    override val newEntity: T
      get() = entity

    override fun toString(): String {
      val newEntityId = (newEntity as? WorkspaceEntityWithSymbolicId)?.symbolicId
      return "Added(newEntity=${newEntityId ?: newEntity})"
    }
  }

  /**
   * Describes an entity which was removed from the storage, directly (via [MutableEntityStorage.removeEntity]) or indirectly (as a child of 
   * another removed entity, or as a result of a batch operation ([replaceBySource][MutableEntityStorage.replaceBySource],
   * [applyChangesFrom][MutableEntityStorage.applyChangesFrom]), or after modification of a reference from a parent entity).
   */
  public data class Removed<T : WorkspaceEntity>(private val entity: T) : EntityChange<T>() {
    override val oldEntity: T
      get() = entity
    override val newEntity: T?
      get() = null

    override fun toString(): String {
      val oldEntityId = (oldEntity as? WorkspaceEntityWithSymbolicId)?.symbolicId
      return "Removed(oldEntity=${oldEntityId ?: oldEntity})"
    }
  }

  /**
   * Describes changes in properties of an entity.
   * Old values of the properties can be obtained via [oldEntity], new values can be obtained via [newEntity].
   */
  public data class Replaced<T : WorkspaceEntity>(override val oldEntity: T, override val newEntity: T) : EntityChange<T>() {
    override fun toString(): String {
      val oldEntityId = (oldEntity as? WorkspaceEntityWithSymbolicId)?.symbolicId
      val newEntityId = (newEntity as? WorkspaceEntityWithSymbolicId)?.symbolicId
      return "Replaced(oldEntity=${oldEntityId ?: oldEntity}, newEntity=${newEntityId ?: newEntity})"
    }
  }
}

/**
 * Marks a property as a key field for [replaceBySource][MutableEntityStorage.replaceBySource] operation.
 * Entities will be compared based on properties with this annotation.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
@ApiStatus.Internal
public annotation class EqualsBy
