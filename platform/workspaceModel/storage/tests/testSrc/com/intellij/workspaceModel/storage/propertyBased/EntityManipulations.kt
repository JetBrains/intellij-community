// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.propertyBased

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.entities.*
import com.intellij.workspaceModel.storage.impl.ClassConversion
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
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
    ChangeEntitySource.create(workspace)
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
      ChildWithOptionalParentManipulation //,

      // Do not enable at the moment. A lot of issues about entities with persistentId
      //NamedEntityManipulation
    )
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
    val modifiableClass = ClassConversion.entityDataToModifiableEntity(ClassConversion.entityToEntityData(entityClass)).java as Class<M>

    val entityId = env.generateValue(EntityIdOfFamilyGenerator.create(storage, entityClass.java.toClassId()), null)
    if (entityId == null) return

    val entity = storage.entityDataByIdOrDie(entityId).createEntity(storage) as E

    val modifications = env.generateValue(Generator.sampledFrom(modifyEntity(env)), null)

    try {
      storage.modifyEntity(modifiableClass, entity, modifications)
      env.logMessage("$entity modified")
    }
    catch (e: PersistentIdAlreadyExistsException) {
      env.logMessage("Cannot modify ${entityClass.simpleName} entity. Persistent id ${e.id} already exists")
    }

    env.logMessage("----------------------------------")
  }
}

private object NamedEntityManipulation : EntityManipulation {
  override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
    return object : AddEntity(storage, "NamedEntity") {
      override fun makeEntity(source: EntitySource, someProperty: String, env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        return try {
          storage.addNamedEntity(someProperty, source) to "Set property for NamedEntity: $someProperty"
        }
        catch (e: PersistentIdAlreadyExistsException) {
          val persistentId = e.id as NameId
          assert(storage.entities(NamedEntity::class.java).any { it.persistentId() == persistentId }) {
            "$persistentId reported as existing, but it's not found"
          }
          null to "NamedEntity with this property isn't added because this persistent id already exists"
        }
      }
    }
  }

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<out WorkspaceEntity, out ModifiableWorkspaceEntity<out WorkspaceEntity>> {
    return object : ModifyEntity<NamedEntity, ModifiableNamedEntity>(NamedEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<ModifiableNamedEntity.() -> Unit> {
        return listOf(modifyStringProperty(ModifiableNamedEntity::name, env))
      }
    }
  }
}

private object ChildWithOptionalParentManipulation : EntityManipulation {
  override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
    return object : AddEntity(storage, "ChildWithOptionalDependency") {
      override fun makeEntity(source: EntitySource, someProperty: String, env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        val classId = ParentEntity::class.java.toClassId()
        val parentId = env.generateValue(Generator.anyOf(
          Generator.constant(null),
          EntityIdOfFamilyGenerator.create(storage, classId)
        ), null)
        val parentEntity = parentId?.let { storage.entityDataByIdOrDie(it).createEntity(storage) as ParentEntity }
        return storage.addChildWithOptionalParentEntity(parentEntity, someProperty, source) to "Select parent for child: $parentId"
      }
    }
  }

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<ChildWithOptionalParentEntity, ModifiableChildWithOptionalParentEntity> {
    return object : ModifyEntity<ChildWithOptionalParentEntity, ModifiableChildWithOptionalParentEntity>(
      ChildWithOptionalParentEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<ModifiableChildWithOptionalParentEntity.() -> Unit> {
        return listOf(
          modifyStringProperty(ModifiableChildWithOptionalParentEntity::childProperty, env),
          modifyNullableProperty(ModifiableChildWithOptionalParentEntity::optionalParent, parentGenerator(storage), env)
        )
      }
    }
  }
}

private object ChildEntityManipulation : EntityManipulation {
  override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
    return object : AddEntity(storage, "Child") {
      override fun makeEntity(source: EntitySource, someProperty: String, env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        val parent = selectParent(storage, env) ?: return null to "Cannot select parent"
        return storage.addChildEntity(parent, someProperty, null, source) to "Selected parent: $parent"
      }
    }
  }

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<ChildEntity, ModifiableChildEntity> {
    return object : ModifyEntity<ChildEntity, ModifiableChildEntity>(ChildEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<ModifiableChildEntity.() -> Unit> {
        return listOf(
          modifyStringProperty(ModifiableChildEntity::childProperty, env),
          modifyNotNullProperty(ModifiableChildEntity::parent, parentGenerator(storage), env)
        )
      }
    }
  }

  private fun selectParent(storage: WorkspaceEntityStorageBuilderImpl, env: ImperativeCommand.Environment): ParentEntity? {
    val classId = ParentEntity::class.java.toClassId()
    val parentId = env.generateValue(EntityIdOfFamilyGenerator.create(storage, classId), null) ?: return null
    return storage.entityDataByIdOrDie(parentId).createEntity(storage) as ParentEntity
  }
}

private object ParentEntityManipulation : EntityManipulation {
  override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
    return object : AddEntity(storage, "Parent") {
      override fun makeEntity(source: EntitySource, someProperty: String, env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        return storage.addParentEntity(someProperty, source) to "parentProperty: $someProperty"
      }
    }
  }

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<ParentEntity, ModifiableParentEntity> {
    return object : ModifyEntity<ParentEntity, ModifiableParentEntity>(ParentEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<ModifiableParentEntity.() -> Unit> {
        return listOf(
          modifyStringProperty(ModifiableParentEntity::parentProperty, env),
          swapElementsInSequence(ModifiableParentEntity::children, env),
          removeInSequence(ModifiableParentEntity::optionalChildren, env)
        )
      }
    }
  }
}

private object SampleEntityManipulation : EntityManipulation {
  override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
    return object : AddEntity(storage, "Sample") {
      override fun makeEntity(source: EntitySource, someProperty: String, env: ImperativeCommand.Environment): Pair<WorkspaceEntity?, String> {
        return storage.addSampleEntity(someProperty, source) to "property: $someProperty"
      }
    }
  }

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<SampleEntity, ModifiableSampleEntity> {
    return object : ModifyEntity<SampleEntity, ModifiableSampleEntity>(SampleEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<ModifiableSampleEntity.() -> Unit> {
        return listOf(
          modifyBooleanProperty(ModifiableSampleEntity::booleanProperty, env),
          modifyStringProperty(ModifiableSampleEntity::stringProperty, env),
          addOrRemoveInList(ModifiableSampleEntity::stringListProperty, randomNames, env)
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

private fun <A> MutableList<A>.swap(index1: Int, index2: Int) {
  val tmp = this[index1]
  this[index1] = this[index2]
  this[index2] = tmp
}
