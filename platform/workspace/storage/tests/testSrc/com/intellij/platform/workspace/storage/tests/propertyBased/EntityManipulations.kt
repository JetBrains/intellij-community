// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.storage.tests.propertyBased

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.*
import com.intellij.platform.workspace.storage.impl.exceptions.SymbolicIdAlreadyExistsException
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.testEntities.entities.*
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.junit.Assert
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1

internal fun getEntityManipulation(workspace: MutableEntityStorageImpl,
                                   detachedEntities: MutableList<WorkspaceEntity> = ArrayList()): Generator<ImperativeCommand>? {
  return Generator.anyOf(
    RemoveSomeEntity.create(workspace),
    EntityManipulation.addManipulations(workspace),
    EntityManipulation.modifyManipulations(workspace),
    EntityManipulation.createDetachedEntities(workspace, detachedEntities),
    ChangeEntitySource.create(workspace),
    EntitiesBySource.create(workspace),
    AddDetachedToStorage.create(workspace, detachedEntities)
  )
}

internal interface EntityManipulation {
  fun addManipulation(storage: MutableEntityStorageImpl): AddEntity
  fun modifyManipulation(storage: MutableEntityStorageImpl): ModifyEntity<out WorkspaceEntity, out WorkspaceEntity.Builder<out WorkspaceEntity>>
  fun addDetachedManipulation(storage: MutableEntityStorageImpl, detachedEntities: MutableList<WorkspaceEntity>): AddEntity {
    return addManipulation(storage)
  }

  companion object {
    fun addManipulations(storage: MutableEntityStorageImpl): Generator<AddEntity> {
      return Generator.sampledFrom(manipulations.map { it.addManipulation(storage) })
    }

    fun modifyManipulations(storage: MutableEntityStorageImpl): Generator<ModifyEntity<*, *>> {
      return Generator.sampledFrom(manipulations.map { it.modifyManipulation(storage) })
    }

    fun createDetachedEntities(storage: MutableEntityStorageImpl, detachedEntities: MutableList<WorkspaceEntity>): Generator<AddEntity> {
      return Generator.sampledFrom(manipulations.map { it.addDetachedManipulation(storage, detachedEntities) })
    }

    private val manipulations = listOf(
      SampleEntityManipulation,
      ParentEntityManipulation,
      ChildEntityManipulation,
      ChildWithOptionalParentManipulation,
      OoParentManipulation,
      OoChildManipulation,
      OoChildWithNullableParentManipulation,

      // Entities with abstractions
      MiddleEntityManipulation,
      AbstractEntities.Left,
      AbstractEntities.Right,

      // Do not enable at the moment. A lot of issues about entities with symbolicId
      //NamedEntityManipulation
    )
  }
}

private class EntitiesBySource(private val storage: MutableEntityStorageImpl) : ImperativeCommand {
  override fun performCommand(env: ImperativeCommand.Environment) {
    val source = env.generateValue(sources, null)

    // Check no exceptions.
    // XXX Can we check anything else?
    storage.entitiesBySource { it == source }
  }

  companion object {
    fun create(workspace: MutableEntityStorageImpl): Generator<EntitiesBySource> = Generator.constant(EntitiesBySource(workspace))
  }
}

private class AddDetachedToStorage(private val storage: MutableEntityStorageImpl,
                                   private val entities: MutableList<WorkspaceEntity>) : ImperativeCommand {
  override fun performCommand(env: ImperativeCommand.Environment) {
    if (entities.isEmpty()) return
    val entityIndex = env.generateValue(Generator.integers(0, entities.size - 1), null)
    val someEntity = entities.removeAt(entityIndex)
    if (someEntity is ModifiableWorkspaceEntityBase<*, *> && someEntity.diff == null) {
      storage.addEntity(someEntity)
      env.logMessage("Added ${someEntity.id.asString()} to storage")
    }
    else {
      env.logMessage("Cannot add an entity to storage")
    }
  }

  companion object {
    fun create(workspace: MutableEntityStorageImpl, entities: MutableList<WorkspaceEntity>): Generator<AddDetachedToStorage> {
      return Generator.constant(AddDetachedToStorage(workspace, entities))
    }
  }
}

// Common for all entities
private class RemoveSomeEntity(private val storage: MutableEntityStorageImpl) : ImperativeCommand {
  override fun performCommand(env: ImperativeCommand.Environment) {
    val id = env.generateValue(EntityIdGenerator.create(storage), null) ?: run {
      env.logMessage("Tried to remove random entity, but failed to select entity")
      return
    }
    storage.removeEntity(storage.entityDataByIdOrDie(id).createEntity(storage))
    Assert.assertNull(storage.entityDataById(id))
    env.logMessage("Entity removed. Id: ${id.asString()}")
  }

  companion object {
    fun create(workspace: MutableEntityStorageImpl): Generator<RemoveSomeEntity> = Generator.constant(RemoveSomeEntity(workspace))
  }
}

private class ChangeEntitySource(private val storage: MutableEntityStorageImpl) : ImperativeCommand {
  override fun performCommand(env: ImperativeCommand.Environment) {
    val id = env.generateValue(EntityIdGenerator.create(storage), null) ?: run {
      env.logMessage("Tried to change entity source, but entity to change isn't found")
      return
    }
    val newSource = env.generateValue(sources, null)
    val entity = storage.entityDataByIdOrDie(id).createEntity(storage)
    val oldEntitySource = entity.entitySource
    storage.modifyEntity(WorkspaceEntity.Builder::class.java, entity) {
      this.entitySource = newSource
    }

    storage.assertConsistency()
    env.logMessage("Entity source changed. Entity: $entity, Old source: $oldEntitySource, New source: $newSource")
  }

  companion object {
    fun create(workspace: MutableEntityStorageImpl): Generator<ChangeEntitySource> {
      return Generator.constant(ChangeEntitySource(workspace))
    }
  }
}

internal abstract class AddEntity(protected val storage: MutableEntityStorageImpl,
                                  protected val entityDescription: String) : ImperativeCommand {
  abstract fun makeEntity(source: EntitySource, someProperty: String, env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String>

  override fun performCommand(env: ImperativeCommand.Environment) {
    val property = env.generateValue(randomNames, null)
    val source = env.generateValue(sources, null)
    val (createdEntity, description) = makeEntity(source, property, env)
    if (createdEntity != null) {
      createdEntity as WorkspaceEntityBase
      Assert.assertNotNull(storage.entityDataById(createdEntity.id))
      env.logMessage("New entity added: $createdEntity. Source: ${createdEntity.entitySource}. $description")
    }
    else {
      env.logMessage("Tried to add $entityDescription but failed because: $description")
    }
  }
}

internal abstract class CreateDetachedEntity(storage: MutableEntityStorageImpl,
                                             entityDescription: String,
                                             private val detachedEntities: MutableList<WorkspaceEntity>) : AddEntity(storage,
                                                                                                                     entityDescription) {
  override fun performCommand(env: ImperativeCommand.Environment) {
    val property = env.generateValue(randomNames, null)
    val source = env.generateValue(sources, null)
    val (createdEntity, description) = makeEntity(source, property, env)
    if (createdEntity != null) {
      createdEntity as WorkspaceEntityBase
      if (createdEntity is ModifiableWorkspaceEntityBase<*, *> && createdEntity.diff == null) {
        detachedEntities.add(createdEntity)
      }
      env.logMessage("New detached entity created: $createdEntity. Source: ${createdEntity.entitySource}. $description")
    }
    else {
      env.logMessage("Tried to add $entityDescription but failed because: $description")
    }
  }
}

internal abstract class ModifyEntity<E : WorkspaceEntity, M : WorkspaceEntity.Builder<E>>(private val entityClass: KClass<E>,
                                                                                          protected val storage: MutableEntityStorageImpl) : ImperativeCommand {
  abstract fun modifyEntity(env: ImperativeCommand.Environment): List<M.() -> Unit>

  final override fun performCommand(env: ImperativeCommand.Environment) {
    val modifiableClass: Class<M>
    modifiableClass = entityClass.java.toBuilderClass() as Class<M>

    val entityId = env.generateValue(EntityIdOfFamilyGenerator.create(storage, entityClass.java.toClassId()), null)
    if (entityId == null) return

    @Suppress("UNCHECKED_CAST") val entity = storage.entityDataByIdOrDie(entityId).createEntity(storage) as E

    env.logMessage("------- modifying entity $entity ----------")
    val modifyEntityAlternatives = modifyEntity(env)
    if (modifyEntityAlternatives.isNotEmpty()) {
      val modifications = env.generateValue(Generator.sampledFrom(modifyEntityAlternatives), null)

      try {
        storage.modifyEntity(modifiableClass, entity, modifications)
        env.logMessage("$entity modified")
      }
      catch (e: SymbolicIdAlreadyExistsException) {
        env.logMessage("Cannot modify ${entityClass.simpleName} entity. Persistent id ${e.id} already exists")
      }
    }

    env.logMessage("----------------------------------")
  }
}

private fun Class<*>.toBuilderClass(): Class<*> {
  return Class.forName("$name\$Builder", true, classLoader)
}

private object NamedEntityManipulation : EntityManipulation {
  override fun addManipulation(storage: MutableEntityStorageImpl): AddEntity {
    return object : AddEntity(storage, "NamedEntity") {
      override fun makeEntity(source: EntitySource,
                              someProperty: String,
                              env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        return try {
          storage.addNamedEntity(someProperty, source = source) to "Set property for NamedEntity: $someProperty"
        }
        catch (e: SymbolicIdAlreadyExistsException) {
          val symbolicId = e.id as NameId
          assert(storage.entities(NamedEntity::class.java).any { it.symbolicId == symbolicId }) {
            "$symbolicId reported as existing, but it's not found"
          }
          null to "NamedEntity with this property isn't added because this symbolic id already exists"
        }
      }
    }
  }

  override fun modifyManipulation(storage: MutableEntityStorageImpl): ModifyEntity<out WorkspaceEntity, out WorkspaceEntity.Builder<out WorkspaceEntity>> {
    return object : ModifyEntity<NamedEntity, NamedEntity.Builder>(NamedEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<NamedEntity.Builder.() -> Unit> {
        return listOf(modifyStringProperty(NamedEntity.Builder::myName, env))
      }
    }
  }
}

private object ChildWithOptionalParentManipulation : EntityManipulation {
  override fun addManipulation(storage: MutableEntityStorageImpl): AddEntity {
    return object : AddEntity(storage, "ChildWithOptionalDependency") {
      override fun makeEntity(source: EntitySource,
                              someProperty: String,
                              env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        val classId = XParentEntity::class.java.toClassId()
        val parentId = env.generateValue(Generator.anyOf(
          Generator.constant(null),
          EntityIdOfFamilyGenerator.create(storage, classId)
        ), null)
        val parentEntity = parentId?.let { storage.entityDataByIdOrDie(it).createEntity(storage) as XParentEntity }
        return storage addEntity XChildWithOptionalParentEntity(someProperty, source) {
          optionalParent = parentEntity
        } to "Select parent for child: ${parentId?.asString()}"
      }
    }
  }

  override fun modifyManipulation(storage: MutableEntityStorageImpl): ModifyEntity<XChildWithOptionalParentEntity, XChildWithOptionalParentEntity.Builder> {
    return object : ModifyEntity<XChildWithOptionalParentEntity, XChildWithOptionalParentEntity.Builder>(
      XChildWithOptionalParentEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<XChildWithOptionalParentEntity.Builder.() -> Unit> {
        return listOf(
          modifyStringProperty(XChildWithOptionalParentEntity.Builder::childProperty, env),
          modifyNullableProperty(XChildWithOptionalParentEntity.Builder::optionalParent, parentGenerator(storage), env)
        )
      }
    }
  }
}

private object OoParentManipulation : EntityManipulation {
  override fun addManipulation(storage: MutableEntityStorageImpl): AddEntity {
    return object : AddEntity(storage, "OoParent") {
      override fun makeEntity(source: EntitySource,
                              someProperty: String,
                              env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        return storage.addOoParentEntity(someProperty, source) to "OoParent. $someProperty"
      }
    }
  }

  override fun modifyManipulation(storage: MutableEntityStorageImpl): ModifyEntity<out WorkspaceEntity, out WorkspaceEntity.Builder<out WorkspaceEntity>> {
    return object : ModifyEntity<OoParentEntity, OoParentEntity.Builder>(OoParentEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<OoParentEntity.Builder.() -> Unit> {
        return listOf(
          modifyStringProperty(OoParentEntity.Builder::parentProperty, env),
          modifyNullableProperty(OoParentEntity.Builder::child,
                                 Generator.sampledFrom(
                                   OoChildEntity(env.generateValue(randomNames, null), env.generateValue(sources, null))
                                 ),
                                 env),
        )
      }
    }
  }
}

private object OoChildManipulation : EntityManipulation {
  override fun addManipulation(storage: MutableEntityStorageImpl): AddEntity {
    return object : AddEntity(storage, "OoChild") {
      override fun makeEntity(source: EntitySource,
                              someProperty: String,
                              env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        val parentEntity = selectParent(storage, env) ?: return null to "Cannot select parent"
        val newChild = storage addEntity OoChildEntity(someProperty, source) {
          this.parentEntity = parentEntity
        }
        return newChild to "Selected parent: $parentEntity"
      }
    }
  }

  override fun modifyManipulation(storage: MutableEntityStorageImpl): ModifyEntity<out WorkspaceEntity, out WorkspaceEntity.Builder<out WorkspaceEntity>> {
    return object : ModifyEntity<OoChildEntity, OoChildEntity.Builder>(OoChildEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<OoChildEntity.Builder.() -> Unit> {
        return listOf(modifyStringProperty(OoChildEntity.Builder::childProperty, env))
      }
    }
  }

  private fun selectParent(storage: MutableEntityStorageImpl, env: ImperativeCommand.Environment): OoParentEntity? {
    val parents = storage.entities(OoParentEntity::class.java).filter { it.child == null }.toList()
    if (parents.isEmpty()) return null

    return env.generateValue(Generator.sampledFrom(parents), null)
  }
}

private object OoChildWithNullableParentManipulation : EntityManipulation {
  override fun addManipulation(storage: MutableEntityStorageImpl): AddEntity {
    return object : AddEntity(storage, "OoChildWithNullableParent") {
      override fun makeEntity(source: EntitySource,
                              someProperty: String,
                              env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        val parentEntity = selectParent(storage, env) ?: return null to "Cannot select parent"
        return storage.addOoChildWithNullableParentEntity(parentEntity, source) to "Selected parent: $parentEntity"
      }
    }
  }

  override fun modifyManipulation(storage: MutableEntityStorageImpl): ModifyEntity<out WorkspaceEntity, out WorkspaceEntity.Builder<out WorkspaceEntity>> {
    return object : ModifyEntity<OoChildWithNullableParentEntity, OoChildWithNullableParentEntity.Builder>(
      OoChildWithNullableParentEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<OoChildWithNullableParentEntity.Builder.() -> Unit> {
        return emptyList()
      }
    }
  }

  private fun selectParent(storage: MutableEntityStorageImpl, env: ImperativeCommand.Environment): OoParentEntity? {
    val parents = storage.entities(OoParentEntity::class.java).filter { it.child == null }.toList()
    if (parents.isEmpty()) return null

    return env.generateValue(Generator.sampledFrom(parents), null)
  }
}

private object MiddleEntityManipulation : EntityManipulation {
  override fun addManipulation(storage: MutableEntityStorageImpl): AddEntity {
    return object : AddEntity(storage, "MiddleEntity") {
      override fun makeEntity(source: EntitySource,
                              someProperty: String,
                              env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        return storage.addMiddleEntity(someProperty, source) to "Property: $someProperty"
      }
    }
  }

  override fun modifyManipulation(storage: MutableEntityStorageImpl): ModifyEntity<out WorkspaceEntity, out WorkspaceEntity.Builder<out WorkspaceEntity>> {
    return object : ModifyEntity<MiddleEntity, MiddleEntity.Builder>(MiddleEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<MiddleEntity.Builder.() -> Unit> {
        return listOf(modifyStringProperty(MiddleEntity.Builder::property, env))
      }
    }
  }
}

private object AbstractEntities {
  object Left : EntityManipulation {
    override fun addManipulation(storage: MutableEntityStorageImpl): AddEntity {
      return object : AddEntity(storage, "LeftEntity") {
        override fun makeEntity(source: EntitySource,
                                someProperty: String,
                                env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
          val children = selectChildren(env, storage).asSequence()
          return storage.addLeftEntity(children, source) to "Children: ${children.toList()}"
        }
      }
    }

    override fun modifyManipulation(storage: MutableEntityStorageImpl): ModifyEntity<out WorkspaceEntity, out WorkspaceEntity.Builder<out WorkspaceEntity>> {
      return object : ModifyEntity<LeftEntity, LeftEntity.Builder>(LeftEntity::class, storage) {
        override fun modifyEntity(env: ImperativeCommand.Environment): List<LeftEntity.Builder.() -> Unit> {
          return listOf(
            swapElementsInList(LeftEntity.Builder::children, env),
            removeInList(LeftEntity.Builder::children, env)
          )
        }
      }
    }
  }

  object Right : EntityManipulation {
    override fun addManipulation(storage: MutableEntityStorageImpl): AddEntity {
      return object : AddEntity(storage, "RightEntity") {
        override fun makeEntity(source: EntitySource,
                                someProperty: String,
                                env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
          val children = selectChildren(env, storage).asSequence()
          return storage.addRightEntity(children, source) to "Children: ${children.toList()}"
        }
      }
    }

    override fun modifyManipulation(storage: MutableEntityStorageImpl): ModifyEntity<out WorkspaceEntity, out WorkspaceEntity.Builder<out WorkspaceEntity>> {
      return object : ModifyEntity<RightEntity, RightEntity.Builder>(RightEntity::class, storage) {
        override fun modifyEntity(env: ImperativeCommand.Environment): List<RightEntity.Builder.() -> Unit> {
          return listOf(
            swapElementsInList(RightEntity.Builder::children, env),
            removeInList(RightEntity.Builder::children, env)
          )
        }
      }
    }
  }

  private fun selectChildren(env: ImperativeCommand.Environment, storage: MutableEntityStorageImpl): Set<BaseEntity> {
    val children = env.generateValue(Generator.integers(0, 3), null)
    val newChildren = mutableSetOf<BaseEntity>()
    repeat(children) {
      val possibleEntities = (storage.entities(LeftEntity::class.java) +
                              storage.entities(RightEntity::class.java) +
                              storage.entities(MiddleEntity::class.java)).toList()
      if (possibleEntities.isNotEmpty()) {
        val newEntity = env.generateValue(Generator.sampledFrom(possibleEntities), null)
        newChildren += newEntity
      }
    }
    return newChildren
  }
}

private object ChildEntityManipulation : EntityManipulation {
  override fun addManipulation(storage: MutableEntityStorageImpl): AddEntity {
    return object : AddEntity(storage, "Child") {
      override fun makeEntity(source: EntitySource,
                              someProperty: String,
                              env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        val parent = selectParent(storage, env) ?: return null to "Cannot select parent"
        return storage addEntity XChildEntity(someProperty, source) {
          parentEntity = parent
        } to "Selected parent: $parent"
      }
    }
  }

  override fun modifyManipulation(storage: MutableEntityStorageImpl): ModifyEntity<XChildEntity, XChildEntity.Builder> {
    return object : ModifyEntity<XChildEntity, XChildEntity.Builder>(XChildEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<XChildEntity.Builder.() -> Unit> {
        return listOf(
          modifyStringProperty(XChildEntity.Builder::childProperty, env),
          modifyNotNullProperty(XChildEntity.Builder::parentEntity, parentGenerator(storage), env)
        )
      }
    }
  }

  private fun selectParent(storage: MutableEntityStorageImpl, env: ImperativeCommand.Environment): XParentEntity? {
    val classId = XParentEntity::class.java.toClassId()
    val parentId = env.generateValue(EntityIdOfFamilyGenerator.create(storage, classId), null) ?: return null
    return storage.entityDataByIdOrDie(parentId).createEntity(storage) as XParentEntity
  }
}

private object ParentEntityManipulation : EntityManipulation {
  override fun addManipulation(storage: MutableEntityStorageImpl): AddEntity {
    return object : AddEntity(storage, "Parent") {
      override fun makeEntity(source: EntitySource,
                              someProperty: String,
                              env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        return storage addEntity XParentEntity(someProperty, source) to "parentProperty: $someProperty"
      }
    }
  }

  override fun modifyManipulation(storage: MutableEntityStorageImpl): ModifyEntity<XParentEntity, XParentEntity.Builder> {
    return object : ModifyEntity<XParentEntity, XParentEntity.Builder>(XParentEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<XParentEntity.Builder.() -> Unit> {
        return listOf(
          modifyStringProperty(XParentEntity.Builder::parentProperty, env),
          swapElementsInList(XParentEntity.Builder::children, env),
          removeInList(XParentEntity.Builder::optionalChildren, env)
        )
      }
    }
  }
}

private object SampleEntityManipulation : EntityManipulation {
  override fun addManipulation(storage: MutableEntityStorageImpl): AddEntity {
    return object : AddEntity(storage, "Sample") {
      override fun makeEntity(source: EntitySource,
                              someProperty: String,
                              env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        return storage addEntity SampleEntity(false, someProperty, ArrayList(), HashMap(),
                                              VirtualFileUrlManagerImpl().getOrCreateFromUri("file:///tmp"),
                                              source) to "property: $someProperty"
      }
    }
  }

  override fun modifyManipulation(storage: MutableEntityStorageImpl): ModifyEntity<SampleEntity, SampleEntity.Builder> {
    return object : ModifyEntity<SampleEntity, SampleEntity.Builder>(SampleEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<SampleEntity.Builder.() -> Unit> {
        return listOf(
          modifyBooleanProperty(SampleEntity.Builder::booleanProperty, env),
          modifyStringProperty(SampleEntity.Builder::stringProperty, env),
          addOrRemoveInList(SampleEntity.Builder::stringListProperty, randomNames, env)
        )
      }
    }
  }

  override fun addDetachedManipulation(storage: MutableEntityStorageImpl, detachedEntities: MutableList<WorkspaceEntity>): AddEntity {
    return object : CreateDetachedEntity(storage, "Sample", detachedEntities) {
      override fun makeEntity(source: EntitySource,
                              someProperty: String,
                              env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        val virtualFileManager = VirtualFileUrlManagerImpl()
        return SampleEntity(false, someProperty, emptyList(), emptyMap(), virtualFileManager.getOrCreateFromUri("file:///tmp"), source) {
          this.children = emptyList()
        } to "property: $someProperty"
      }
    }
  }
}

private fun <B : WorkspaceEntity, A : WorkspaceEntity.Builder<B>, T> modifyNotNullProperty(
  property: KMutableProperty1<A, T>,
  takeFrom: Generator<T?>,
  env: ImperativeCommand.Environment
): A.() -> Unit {
  return {
    val value = env.generateValue(takeFrom, null)
    if (value != null) {
      env.logMessage("Change `${property.name}` to $value")
      property.set(this, value)
    }
  }
}

private fun <B : WorkspaceEntity, A : WorkspaceEntity.Builder<B>, T> modifyNullableProperty(
  property: KMutableProperty1<A, T?>,
  takeFrom: Generator<T?>,
  env: ImperativeCommand.Environment
): A.() -> Unit {
  return {
    val value = env.generateValue(takeFrom, null)
    env.logMessage("Change `${property.name}` to $value")
    property.set(this, value)
  }
}

private fun <B : WorkspaceEntity, A : WorkspaceEntity.Builder<B>> modifyStringProperty(property: KMutableProperty1<A, String>,
                                                                                       env: ImperativeCommand.Environment): A.() -> Unit {
  return modifyNotNullProperty(property, randomNames, env)
}

private fun <B : WorkspaceEntity, A : WorkspaceEntity.Builder<B>> modifyBooleanProperty(property: KMutableProperty1<A, Boolean>,
                                                                                        env: ImperativeCommand.Environment): A.() -> Unit {
  return modifyNotNullProperty(property, Generator.booleans(), env)
}

private fun <B : WorkspaceEntity, A : WorkspaceEntity.Builder<B>, T> addOrRemoveInList(property: KMutableProperty1<A, MutableList<T>>,
                                                                                       takeFrom: Generator<T>,
                                                                                       env: ImperativeCommand.Environment): A.() -> Unit {
  return {
    val removeValue = env.generateValue(Generator.booleans(), null)
    val value = property.getter.call(this)
    if (removeValue) {
      if (value.isNotEmpty()) {
        val i = env.generateValue(Generator.integers(0, value.lastIndex), null)
        env.logMessage("Remove item from ${property.name}. Index: $i, Element ${value[i]}")
        property.set(this, value.toMutableList().also { it.removeAt(i) })
      }
    }
    else {
      val newElement = env.generateValue(takeFrom, "Adding new element to ${property.name}: %s")
      value.add(newElement)
    }
  }
}

private fun <B : WorkspaceEntity, A : WorkspaceEntity.Builder<B>, T> swapElementsInSequence(property: KMutableProperty1<A, Sequence<T>>,
                                                                                            env: ImperativeCommand.Environment): A.() -> Unit {
  return {
    val propertyList = property.getter.call(this).toMutableList()
    if (propertyList.size > 2) {
      val index1 = env.generateValue(Generator.integers(0, propertyList.lastIndex), null)
      val index2 = env.generateValue(Generator.integers(0, propertyList.lastIndex), null)
      env.logMessage(
        "Change ${property.name}. Swap 2 elements: idx1: $index1, idx2: $index2, value1: ${propertyList[index1]}, value2: ${propertyList[index2]}")

      property.set(this, propertyList.also { it.swap(index1, index2) }.asSequence())
    }
  }
}

private fun <B : WorkspaceEntity, A : WorkspaceEntity.Builder<B>, T> swapElementsInList(property: KMutableProperty1<A, List<T>>,
                                                                                        env: ImperativeCommand.Environment): A.() -> Unit {
  return {
    val propertyList = property.getter.call(this).toMutableList()
    if (propertyList.size > 2) {
      val index1 = env.generateValue(Generator.integers(0, propertyList.lastIndex), null)
      val index2 = env.generateValue(Generator.integers(0, propertyList.lastIndex), null)
      env.logMessage(
        "Change ${property.name}. Swap 2 elements: idx1: $index1, idx2: $index2, value1: ${propertyList[index1]}, value2: ${propertyList[index2]}")

      property.set(this, propertyList.also { it.swap(index1, index2) })
    }
  }
}

private fun <B : WorkspaceEntity, A : WorkspaceEntity.Builder<B>, T> removeInSequence(property: KMutableProperty1<A, Sequence<T>>,
                                                                                      env: ImperativeCommand.Environment): A.() -> Unit {
  return {
    val value = property.getter.call(this)
    if (value.any()) {
      val valueList = value.toMutableList()
      val i = env.generateValue(Generator.integers(0, valueList.lastIndex), null)
      env.logMessage("Remove item from ${property.name}. Index: $i, Element ${valueList[i]}")
      valueList.removeAt(i)
      property.set(this, valueList.asSequence())
    }
  }
}

private fun <B : WorkspaceEntity, A : WorkspaceEntity.Builder<B>, T> removeInList(property: KMutableProperty1<A, List<T>>,
                                                                                  env: ImperativeCommand.Environment): A.() -> Unit {
  return {
    val value = property.getter.call(this)
    if (value.any()) {
      val valueList = value.toMutableList()
      val i = env.generateValue(Generator.integers(0, valueList.lastIndex), null)
      env.logMessage("Remove item from ${property.name}. Index: $i, Element ${valueList[i]}")
      valueList.removeAt(i)
      property.set(this, valueList)
    }
  }
}

private fun <A> MutableList<A>.swap(index1: Int, index2: Int) {
  val tmp = this[index1]
  this[index1] = this[index2]
  this[index2] = tmp
}
