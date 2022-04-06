// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.propertyBased

import com.intellij.workspaceModel.storage.ClassConversion
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.entities.addChildEntity
import com.intellij.workspaceModel.storage.entities.addChildWithOptionalParentEntity
import com.intellij.workspaceModel.storage.entities.addParentEntity
import com.intellij.workspaceModel.storage.entities.addSampleEntity
import com.intellij.workspaceModel.storage.entities.api.*
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.storage.impl.exceptions.PersistentIdAlreadyExistsException
import com.intellij.workspaceModel.storage.impl.toClassId
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.junit.Assert
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1

internal fun getEntityManipulation(workspace: WorkspaceEntityStorageBuilderImpl): Generator<ImperativeCommand>? {
  return Generator.anyOf(
    RemoveSomeEntity.create(workspace),
    EntityManipulation.addManipulations(workspace),
    EntityManipulation.modifyManipulations(workspace),
    ChangeEntitySource.create(workspace),
    EntitiesBySource.create(workspace),
  )
}

internal interface EntityManipulation {
  fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity
  fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<out WorkspaceEntity, out ModifiableWorkspaceEntity<out WorkspaceEntity>>

  companion object {
    fun addManipulations(storage: WorkspaceEntityStorageBuilderImpl): Generator<AddEntity> {
      return Generator.sampledFrom(manipulations.map { it.addManipulation(storage) })
    }

    fun modifyManipulations(storage: WorkspaceEntityStorageBuilderImpl): Generator<ModifyEntity<*, *>> {
      return Generator.sampledFrom(manipulations.map { it.modifyManipulation(storage) })
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

      // Do not enable at the moment. A lot of issues about entities with persistentId
      //NamedEntityManipulation
    )
  }
}

private class EntitiesBySource(private val storage: WorkspaceEntityStorageBuilderImpl) : ImperativeCommand {
  override fun performCommand(env: ImperativeCommand.Environment) {
    val source = env.generateValue(sources, null)

    // Check no exceptions.
    // XXX Can we check anything else?
    storage.entitiesBySource { it == source }
  }

  companion object {
    fun create(workspace: WorkspaceEntityStorageBuilderImpl): Generator<EntitiesBySource> = Generator.constant(EntitiesBySource(workspace))
  }
}

// Common for all entities
private class RemoveSomeEntity(private val storage: WorkspaceEntityStorageBuilderImpl) : ImperativeCommand {
  override fun performCommand(env: ImperativeCommand.Environment) {
    val id = env.generateValue(EntityIdGenerator.create(storage), null) ?: run {
      env.logMessage("Tried to remove random entity, but failed to select entity")
      return
    }
    storage.removeEntity(storage.entityDataByIdOrDie(id).createEntity(storage))
    Assert.assertNull(storage.entityDataById(id))
    env.logMessage("Entity removed. Id: $id")
  }

  companion object {
    fun create(workspace: WorkspaceEntityStorageBuilderImpl): Generator<RemoveSomeEntity> = Generator.constant(RemoveSomeEntity(workspace))
  }
}

private class ChangeEntitySource(private val storage: WorkspaceEntityStorageBuilderImpl) : ImperativeCommand {
  override fun performCommand(env: ImperativeCommand.Environment) {
    val id = env.generateValue(EntityIdGenerator.create(storage), null) ?: run {
      env.logMessage("Tried to change entity source, but entity to change isn't found")
      return
    }
    val newSource = env.generateValue(sources, null)
    val entity = storage.entityDataByIdOrDie(id).createEntity(storage)
    val oldEntitySource = entity.entitySource
    storage.changeSource(entity, newSource)

    env.logMessage("Entity source changed. Entity: $entity, Old source: $oldEntitySource, New source: $newSource")
  }

  companion object {
    fun create(workspace: WorkspaceEntityStorageBuilderImpl): Generator<ChangeEntitySource> {
      return Generator.constant(ChangeEntitySource(workspace))
    }
  }
}

internal abstract class AddEntity(protected val storage: WorkspaceEntityStorageBuilderImpl,
                                  private val entityDescription: String) : ImperativeCommand {
  abstract fun makeEntity(source: EntitySource, someProperty: String, env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String>

  final override fun performCommand(env: ImperativeCommand.Environment) {
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

internal abstract class ModifyEntity<E : WorkspaceEntity, M : ModifiableWorkspaceEntity<E>>(private val entityClass: KClass<E>,
                                                                                            protected val storage: WorkspaceEntityStorageBuilderImpl) : ImperativeCommand {
  abstract fun modifyEntity(env: ImperativeCommand.Environment): List<M.() -> Unit>

  final override fun performCommand(env: ImperativeCommand.Environment) {
    val modifiableClass: Class<M>
    if (isOldApi(entityClass.java)) {
      @Suppress("UNCHECKED_CAST")
      modifiableClass = ClassConversion.entityDataToModifiableEntity(ClassConversion.entityToEntityData(entityClass)).java as Class<M>
    } else {
      modifiableClass = ClassConversion.entityDataToModifiableEntityNew(ClassConversion.entityToEntityData(entityClass)).java as Class<M>
    }

    val entityId = env.generateValue(EntityIdOfFamilyGenerator.create(storage, entityClass.java.toClassId()), null)
    if (entityId == null) return

    @Suppress("UNCHECKED_CAST") val entity = storage.entityDataByIdOrDie(entityId).createEntity(storage) as E

    val modifyEntityAlternatives = modifyEntity(env)
    if (modifyEntityAlternatives.isNotEmpty()) {
      val modifications = env.generateValue(Generator.sampledFrom(modifyEntityAlternatives), null)

      try {
        storage.modifyEntity(modifiableClass, entity, modifications)
        env.logMessage("$entity modified")
      }
      catch (e: PersistentIdAlreadyExistsException) {
        env.logMessage("Cannot modify ${entityClass.simpleName} entity. Persistent id ${e.id} already exists")
      }
    }

    env.logMessage("----------------------------------")
  }


  private fun <E: WorkspaceEntity> isOldApi(entityClass: Class<E>): Boolean {
    val entityData: KClass<WorkspaceEntityData<E>> = ClassConversion.entityToEntityData(entityClass.kotlin)
    val modifiableEntity: KClass<ModifiableWorkspaceEntity<E>> = try {
      ClassConversion.entityDataToModifiableEntity(entityData)
    }
    catch (e: Exception) {
      return false
    }
    return !entityClass.isAssignableFrom(modifiableEntity.java)
  }
}

private object NamedEntityManipulation : EntityManipulation {
  override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
    return object : AddEntity(storage, "NamedEntity") {
      override fun makeEntity(source: EntitySource,
                              someProperty: String,
                              env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        return try {
          storage.addNamedEntity(someProperty, source = source) to "Set property for NamedEntity: $someProperty"
        }
        catch (e: PersistentIdAlreadyExistsException) {
          val persistentId = e.id as NameId
          assert(storage.entities(NamedEntity::class.java).any { it.persistentId == persistentId }) {
            "$persistentId reported as existing, but it's not found"
          }
          null to "NamedEntity with this property isn't added because this persistent id already exists"
        }
      }
    }
  }

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<out WorkspaceEntity, out ModifiableWorkspaceEntity<out WorkspaceEntity>> {
    return object : ModifyEntity<NamedEntity, NamedEntityImpl.Builder>(NamedEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<NamedEntityImpl.Builder.() -> Unit> {
        return listOf(modifyStringProperty(NamedEntityImpl.Builder::myName, env))
      }
    }
  }
}

private object ChildWithOptionalParentManipulation : EntityManipulation {
  override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
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
        return storage.addChildWithOptionalParentEntity(parentEntity, someProperty, source) to "Select parent for child: $parentId"
      }
    }
  }

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<XChildWithOptionalParentEntity, XChildWithOptionalParentEntityImpl.Builder> {
    return object : ModifyEntity<XChildWithOptionalParentEntity, XChildWithOptionalParentEntityImpl.Builder>(
      XChildWithOptionalParentEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<XChildWithOptionalParentEntityImpl.Builder.() -> Unit> {
        return listOf(
          modifyStringProperty(XChildWithOptionalParentEntityImpl.Builder::childProperty, env),
          modifyNullableProperty(XChildWithOptionalParentEntityImpl.Builder::optionalParent, parentGenerator(storage), env)
        )
      }
    }
  }
}

private object OoParentManipulation : EntityManipulation {
  override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
    return object : AddEntity(storage, "OoParent") {
      override fun makeEntity(source: EntitySource,
                              someProperty: String,
                              env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        return storage.addOoParentEntity(someProperty, source) to "OoParent. $someProperty"
      }
    }
  }

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<out WorkspaceEntity, out ModifiableWorkspaceEntity<out WorkspaceEntity>> {
    return object : ModifyEntity<OoParentEntity, OoParentEntityImpl.Builder>(OoParentEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<OoParentEntityImpl.Builder.() -> Unit> {
        return listOf(modifyStringProperty(OoParentEntityImpl.Builder::parentProperty, env))
      }
    }
  }
}

private object OoChildManipulation : EntityManipulation {
  override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
    return object : AddEntity(storage, "OoChild") {
      override fun makeEntity(source: EntitySource,
                              someProperty: String,
                              env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        val parentEntity = selectParent(storage, env) ?: return null to "Cannot select parent"
        return storage.addOoChildEntity(parentEntity, someProperty, source) to "Selected parent: $parentEntity"
      }
    }
  }

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<out WorkspaceEntity, out ModifiableWorkspaceEntity<out WorkspaceEntity>> {
    return object : ModifyEntity<OoChildEntity, OoChildEntityImpl.Builder>(OoChildEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<OoChildEntityImpl.Builder.() -> Unit> {
        return listOf(modifyStringProperty(OoChildEntityImpl.Builder::childProperty, env))
      }
    }
  }

  private fun selectParent(storage: WorkspaceEntityStorageBuilderImpl, env: ImperativeCommand.Environment): OoParentEntity? {
    val parents = storage.entities(OoParentEntity::class.java).filter { it.child == null }.toList()
    if (parents.isEmpty()) return null

    return env.generateValue(Generator.sampledFrom(parents), null)
  }
}

private object OoChildWithNullableParentManipulation : EntityManipulation {
  override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
    return object : AddEntity(storage, "OoChildWithNullableParent") {
      override fun makeEntity(source: EntitySource,
                              someProperty: String,
                              env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        val parentEntity = selectParent(storage, env) ?: return null to "Cannot select parent"
        return storage.addOoChildWithNullableParentEntity(parentEntity, source) to "Selected parent: $parentEntity"
      }
    }
  }

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<out WorkspaceEntity, out ModifiableWorkspaceEntity<out WorkspaceEntity>> {
    return object : ModifyEntity<OoChildWithNullableParentEntity, OoChildWithNullableParentEntityImpl.Builder>(
      OoChildWithNullableParentEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<OoChildWithNullableParentEntityImpl.Builder.() -> Unit> {
        return emptyList()
      }
    }
  }

  private fun selectParent(storage: WorkspaceEntityStorageBuilderImpl, env: ImperativeCommand.Environment): OoParentEntity? {
    val parents = storage.entities(OoParentEntity::class.java).filter { it.child == null }.toList()
    if (parents.isEmpty()) return null

    return env.generateValue(Generator.sampledFrom(parents), null)
  }
}

private object MiddleEntityManipulation : EntityManipulation {
  override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
    return object : AddEntity(storage, "MiddleEntity") {
      override fun makeEntity(source: EntitySource,
                              someProperty: String,
                              env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        return storage.addMiddleEntity(someProperty, source) to "Property: $someProperty"
      }
    }
  }

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<out WorkspaceEntity, out ModifiableWorkspaceEntity<out WorkspaceEntity>> {
    return object : ModifyEntity<MiddleEntity, MiddleEntityImpl.Builder>(MiddleEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<MiddleEntityImpl.Builder.() -> Unit> {
        return listOf(modifyStringProperty(MiddleEntityImpl.Builder::property, env))
      }
    }
  }
}

private object AbstractEntities {
  object Left : EntityManipulation {
    override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
      return object : AddEntity(storage, "LeftEntity") {
        override fun makeEntity(source: EntitySource,
                                someProperty: String,
                                env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
          val children = selectChildren(env, storage).asSequence()
          return storage.addLeftEntity(children, source) to ""
        }
      }
    }

    override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<out WorkspaceEntity, out ModifiableWorkspaceEntity<out WorkspaceEntity>> {
      return object : ModifyEntity<LeftEntity, LeftEntityImpl.Builder>(LeftEntity::class, storage) {
        override fun modifyEntity(env: ImperativeCommand.Environment): List<LeftEntityImpl.Builder.() -> Unit> {
          return listOf(
            swapElementsInList(LeftEntityImpl.Builder::children, env),
            removeInList(LeftEntityImpl.Builder::children, env)
          )
        }
      }
    }
  }

  object Right : EntityManipulation {
    override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
      return object : AddEntity(storage, "RightEntity") {
        override fun makeEntity(source: EntitySource,
                                someProperty: String,
                                env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
          val children = selectChildren(env, storage).asSequence()
          return storage.addRightEntity(children, source) to ""
        }
      }
    }

    override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<out WorkspaceEntity, out ModifiableWorkspaceEntity<out WorkspaceEntity>> {
      return object : ModifyEntity<RightEntity, RightEntityImpl.Builder>(RightEntity::class, storage) {
        override fun modifyEntity(env: ImperativeCommand.Environment): List<RightEntityImpl.Builder.() -> Unit> {
          return listOf(
            swapElementsInList(RightEntityImpl.Builder::children, env),
            removeInList(RightEntityImpl.Builder::children, env)
          )
        }
      }
    }
  }

  private fun selectChildren(env: ImperativeCommand.Environment, storage: WorkspaceEntityStorageBuilderImpl): Set<BaseEntity> {
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
  override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
    return object : AddEntity(storage, "Child") {
      override fun makeEntity(source: EntitySource,
                              someProperty: String,
                              env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        val parent = selectParent(storage, env) ?: return null to "Cannot select parent"
        return storage.addChildEntity(parent, someProperty, null, source) to "Selected parent: $parent"
      }
    }
  }

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<XChildEntity, XChildEntityImpl.Builder> {
    return object : ModifyEntity<XChildEntity, XChildEntityImpl.Builder>(XChildEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<XChildEntityImpl.Builder.() -> Unit> {
        return listOf(
          modifyStringProperty(XChildEntityImpl.Builder::childProperty, env),
          modifyNotNullProperty(XChildEntityImpl.Builder::parentEntity, parentGenerator(storage), env)
        )
      }
    }
  }

  private fun selectParent(storage: WorkspaceEntityStorageBuilderImpl, env: ImperativeCommand.Environment): XParentEntity? {
    val classId = XParentEntity::class.java.toClassId()
    val parentId = env.generateValue(EntityIdOfFamilyGenerator.create(storage, classId), null) ?: return null
    return storage.entityDataByIdOrDie(parentId).createEntity(storage) as XParentEntity
  }
}

private object ParentEntityManipulation : EntityManipulation {
  override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
    return object : AddEntity(storage, "Parent") {
      override fun makeEntity(source: EntitySource,
                              someProperty: String,
                              env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        return storage.addParentEntity(someProperty, source) to "parentProperty: $someProperty"
      }
    }
  }

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<XParentEntity, XParentEntityImpl.Builder> {
    return object : ModifyEntity<XParentEntity, XParentEntityImpl.Builder>(XParentEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<XParentEntityImpl.Builder.() -> Unit> {
        return listOf(
          modifyStringProperty(XParentEntityImpl.Builder::parentProperty, env),
          swapElementsInList(XParentEntityImpl.Builder::children, env),
          removeInList(XParentEntityImpl.Builder::optionalChildren, env)
        )
      }
    }
  }
}

private object SampleEntityManipulation : EntityManipulation {
  override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
    return object : AddEntity(storage, "Sample") {
      override fun makeEntity(source: EntitySource,
                              someProperty: String,
                              env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        return storage.addSampleEntity(someProperty, source) to "property: $someProperty"
      }
    }
  }

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<SampleEntity, SampleEntityImpl.Builder> {
    return object : ModifyEntity<SampleEntity, SampleEntityImpl.Builder>(SampleEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<SampleEntityImpl.Builder.() -> Unit> {
        return listOf(
          modifyBooleanProperty(SampleEntityImpl.Builder::booleanProperty, env),
          modifyStringProperty(SampleEntityImpl.Builder::stringProperty, env),
          addOrRemoveInList(SampleEntityImpl.Builder::stringListProperty, randomNames, env)
        )
      }
    }
  }
}

private fun <B : WorkspaceEntity, A : ModifiableWorkspaceEntity<B>, T> modifyNotNullProperty(
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

private fun <B : WorkspaceEntity, A : ModifiableWorkspaceEntity<B>, T> modifyNullableProperty(
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

private fun <B : WorkspaceEntity, A : ModifiableWorkspaceEntity<B>> modifyStringProperty(property: KMutableProperty1<A, String>,
                                                                                         env: ImperativeCommand.Environment): A.() -> Unit {
  return modifyNotNullProperty(property, randomNames, env)
}

private fun <B : WorkspaceEntity, A : ModifiableWorkspaceEntity<B>> modifyBooleanProperty(property: KMutableProperty1<A, Boolean>,
                                                                                          env: ImperativeCommand.Environment): A.() -> Unit {
  return modifyNotNullProperty(property, Generator.booleans(), env)
}

private fun <B : WorkspaceEntity, A : ModifiableWorkspaceEntity<B>, T> addOrRemoveInList(property: KMutableProperty1<A, List<T>>,
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
      property.set(this, value + newElement)
    }
  }
}

private fun <B : WorkspaceEntity, A : ModifiableWorkspaceEntity<B>, T> swapElementsInSequence(property: KMutableProperty1<A, Sequence<T>>,
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

private fun <B : WorkspaceEntity, A : ModifiableWorkspaceEntity<B>, T> swapElementsInList(property: KMutableProperty1<A, List<T>>,
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

private fun <B : WorkspaceEntity, A : ModifiableWorkspaceEntity<B>, T> removeInSequence(property: KMutableProperty1<A, Sequence<T>>,
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

private fun <B : WorkspaceEntity, A : ModifiableWorkspaceEntity<B>, T> removeInList(property: KMutableProperty1<A, List<T>>,
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
