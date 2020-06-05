// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.propertyBased

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.entities.*
import com.intellij.workspaceModel.storage.impl.ClassConversion
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
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
    EntityManipulation.modifyManipulations(workspace)
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
      ChildWithOptionalParentManipulation
    )
  }
}

// Common for all entities
private class RemoveSomeEntity(private val storage: WorkspaceEntityStorageBuilderImpl) : ImperativeCommand {
  override fun performCommand(env: ImperativeCommand.Environment) {
    env.logMessage("Trying to remove random entity")
    val id = env.generateValue(EntityIdGenerator.create(storage), "Generate random EntityId: %s") ?: return
    storage.removeEntity(storage.entityDataByIdOrDie(id).createEntity(storage))
    Assert.assertNull(storage.entityDataById(id))
    env.logMessage("Entity removed")
    env.logMessage("-------------------------")
  }

  companion object {
    fun create(workspace: WorkspaceEntityStorageBuilderImpl): Generator<RemoveSomeEntity> = Generator.constant(RemoveSomeEntity(workspace))
  }
}

internal abstract class AddEntity(protected val storage: WorkspaceEntityStorageBuilderImpl,
                                  private val entityDescription: String) : ImperativeCommand {
  abstract fun makeEntity(source: EntitySource, someProperty: String, env: ImperativeCommand.Environment): WorkspaceEntity?

  final override fun performCommand(env: ImperativeCommand.Environment) {
    env.logMessage("Trying to add $entityDescription entity")
    val property = env.generateValue(randomNames, null)
    val source = env.generateValue(sources, null)
    val createdEntity = makeEntity(source, property, env) as? WorkspaceEntityBase
    if (createdEntity != null) {
      Assert.assertNotNull(storage.entityDataById(createdEntity.id))
      env.logMessage("New entity added: $createdEntity. Source: ${createdEntity.entitySource}")
      env.logMessage("--------------------------------")
    }
  }
}

internal abstract class ModifyEntity<E : WorkspaceEntity, M : ModifiableWorkspaceEntity<E>>(private val entityClass: KClass<E>,
                                                                                            protected val storage: WorkspaceEntityStorageBuilderImpl) : ImperativeCommand {
  abstract fun modifyEntity(env: ImperativeCommand.Environment): List<M.() -> Unit>

  final override fun performCommand(env: ImperativeCommand.Environment) {
    val modifiableClass = ClassConversion.entityDataToModifiableEntity(ClassConversion.entityToEntityData(entityClass)).java as Class<M>

    env.logMessage("Trying to modify ${entityClass.simpleName} entity")

    val entityId = env.generateValue(EntityIdOfFamilyGenerator.create(storage, entityClass.java.toClassId()), null)
    if (entityId == null) return

    val entity = storage.entityDataByIdOrDie(entityId).createEntity(storage) as E

    val modifications = env.generateValue(Generator.sampledFrom(modifyEntity(env)), null)

    storage.modifyEntity(modifiableClass, entity, modifications)

    env.logMessage("----------------------------------")
  }
}

private object ChildWithOptionalParentManipulation : EntityManipulation {
  override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
    return object : AddEntity(storage, "ChildWithOptionalDependency") {
      override fun makeEntity(source: EntitySource, someProperty: String, env: ImperativeCommand.Environment): WorkspaceEntity? {
        val classId = ParentEntity::class.java.toClassId()
        val parentId = env.generateValue(Generator.anyOf(
          Generator.constant(null),
          EntityIdOfFamilyGenerator.create(storage, classId)
        ), "Select parent for child: %s")
        val parentEntity = parentId?.let { storage.entityDataByIdOrDie(it).createEntity(storage) as ParentEntity }
        return storage.addChildWithOptionalParentEntity(parentEntity, someProperty, source)
      }
    }
  }

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<ChildWithOptionalParentEntity, ModifiableChildWithOptionalParentEntity> {
    return object : ModifyEntity<ChildWithOptionalParentEntity, ModifiableChildWithOptionalParentEntity>(
      ChildWithOptionalParentEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<ModifiableChildWithOptionalParentEntity.() -> Unit> {
        return listOf(
          modifyStringProperty(ModifiableChildWithOptionalParentEntity::childProperty, env),
          modifyNullableProperty(ModifiableChildWithOptionalParentEntity::optionalParent, parentGetter(storage), env)
        )
      }
    }
  }
}

private object ChildEntityManipulation : EntityManipulation {
  override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
    return object : AddEntity(storage, "Child") {
      override fun makeEntity(source: EntitySource, someProperty: String, env: ImperativeCommand.Environment): WorkspaceEntity? {
        val parent = selectParent(storage, env) ?: return null
        return storage.addChildEntity(parent, someProperty, null, source)
      }
    }
  }

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<ChildEntity, ModifiableChildEntity> {
    return object : ModifyEntity<ChildEntity, ModifiableChildEntity>(ChildEntity::class, storage) {
      override fun modifyEntity(env: ImperativeCommand.Environment): List<ModifiableChildEntity.() -> Unit> {
        return listOf(
          modifyStringProperty(ModifiableChildEntity::childProperty, env),
          modifyNotNullProperty(ModifiableChildEntity::parent, parentGetter(storage), env)
        )
      }
    }
  }

  private fun selectParent(storage: WorkspaceEntityStorageBuilderImpl, env: ImperativeCommand.Environment): ParentEntity? {
    val classId = ParentEntity::class.java.toClassId()
    val parentId = env.generateValue(EntityIdOfFamilyGenerator.create(storage, classId), "Select parent for child: %s") ?: return null
    return storage.entityDataByIdOrDie(parentId).createEntity(storage) as ParentEntity
  }
}

private object ParentEntityManipulation : EntityManipulation {
  override fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity {
    return object : AddEntity(storage, "Parent") {
      override fun makeEntity(source: EntitySource, someProperty: String, env: ImperativeCommand.Environment): WorkspaceEntity? {
        return storage.addParentEntity(someProperty, source)
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
      override fun makeEntity(source: EntitySource, someProperty: String, env: ImperativeCommand.Environment): WorkspaceEntity? {
        return storage.addSampleEntity(someProperty, source)
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

private fun <B : WorkspaceEntity, A : ModifiableWorkspaceEntity<B>, T> modifyNotNullProperty(property: KMutableProperty1<A, T>,
                                                                                             takeFrom: Generator<T?>,
                                                                                             env: ImperativeCommand.Environment): A.() -> Unit {
  return {
    val value = env.generateValue(takeFrom, null)
    if (value != null) {
      env.logMessage("Change ${property.name} to %s")
      property.set(this, value)
    }
  }
}

private fun <B : WorkspaceEntity, A : ModifiableWorkspaceEntity<B>, T> modifyNullableProperty(property: KMutableProperty1<A, T?>,
                                                                                              takeFrom: Generator<T?>,
                                                                                              env: ImperativeCommand.Environment): A.() -> Unit {
  return {
    val value = env.generateValue(takeFrom, null)
    env.logMessage("Change ${property.name} to %s")
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

private inline fun <reified T : WorkspaceEntity> parentGetter(storage: WorkspaceEntityStorageBuilderImpl): Generator<T?> {
  return Generator.from {
    val classId = T::class.java.toClassId()
    val parentId = it.generate(EntityIdOfFamilyGenerator.create(storage, classId)) ?: return@from null
    storage.entityDataByIdOrDie(parentId).createEntity(storage) as T
  }
}


private fun <A> MutableList<A>.swap(index1: Int, index2: Int) {
  val tmp = this[index1]
  this[index1] = this[index2]
  this[index2] = tmp
}
