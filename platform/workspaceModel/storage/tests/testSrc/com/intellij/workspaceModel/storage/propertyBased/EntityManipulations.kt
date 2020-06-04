// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.propertyBased

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.entities.*
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.storage.impl.toClassId
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.junit.Assert

internal fun getEntityManipulation(workspace: WorkspaceEntityStorageBuilderImpl): Generator<ImperativeCommand>? {
  return Generator.anyOf(
    RemoveSomeEntity.create(workspace),
    EntityManipulation.addManipulations(workspace),
    EntityManipulation.modifyManipulations(workspace)
  )
}

internal interface EntityManipulation {
  fun addManipulation(storage: WorkspaceEntityStorageBuilderImpl): AddEntity
  fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<out WorkspaceEntity>

  companion object {
    fun addManipulations(storage: WorkspaceEntityStorageBuilderImpl): Generator<AddEntity> {
      return Generator.sampledFrom(manipulations.map { it.addManipulation(storage) })
    }

    fun modifyManipulations(storage: WorkspaceEntityStorageBuilderImpl): Generator<ModifyEntity<*>> {
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

internal abstract class ModifyEntity<E : WorkspaceEntity>(protected val storage: WorkspaceEntityStorageBuilderImpl,
                                                          private val entityDescription: String,
                                                          private val classId: Int) : ImperativeCommand {
  abstract fun modifyEntity(entity: E, someProperty: String, env: ImperativeCommand.Environment): Boolean

  final override fun performCommand(env: ImperativeCommand.Environment) {
    env.logMessage("Trying to modify $entityDescription entity")
    val property = env.generateValue(randomNames, null)

    val entityId = env.generateValue(EntityIdOfFamilyGenerator.create(storage, classId), null)
    if (entityId == null) return

    val entity = storage.entityDataByIdOrDie(entityId).createEntity(storage) as E
    modifyEntity(entity, property, env)
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

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<ChildWithOptionalParentEntity> {
    return object : ModifyEntity<ChildWithOptionalParentEntity>(storage, "ChildWithOptionalParent",
                                                                ChildWithOptionalParentEntity::class.java.toClassId()) {
      override fun modifyEntity(entity: ChildWithOptionalParentEntity, someProperty: String, env: ImperativeCommand.Environment): Boolean {
        val selectedModification = env.generateValue(Generator.integers(0, 0), null)
        val modification: ModifiableChildWithOptionalParentEntity.() -> Unit = when (selectedModification) {
          0 -> { -> childProperty = env.generateValue(randomNames, "Change childProperty to %s") }
          else -> error("Undefined modification")
        }
        storage.modifyEntity(ModifiableChildWithOptionalParentEntity::class.java, entity, modification)
        return true
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

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<ChildEntity> {
    return object : ModifyEntity<ChildEntity>(storage, "Child", ChildEntity::class.java.toClassId()) {
      override fun modifyEntity(entity: ChildEntity, someProperty: String, env: ImperativeCommand.Environment): Boolean {

        val modification = env.generateValue(Generator.sampledFrom<ModifiableChildEntity.() -> Unit>(
          // Change child property
          { childProperty = env.generateValue(randomNames, "Change childProperty to %s") },

          // Change parent of child
          {
            val newParent = selectParent(storage, env) ?: return@sampledFrom
            parent = newParent
            env.logMessage("Set new parent for child: $newParent")
          }
        ), null)

        storage.modifyEntity(ModifiableChildEntity::class.java, entity, modification)
        return true
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

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<ParentEntity> {
    return object : ModifyEntity<ParentEntity>(storage, "Parent", ParentEntity::class.java.toClassId()) {
      override fun modifyEntity(entity: ParentEntity, someProperty: String, env: ImperativeCommand.Environment): Boolean {

        val modification = env.generateValue(Generator.sampledFrom<ModifiableParentEntity.() -> Unit>(
          // Change parent property
          { parentProperty = env.generateValue(randomNames, "Change parentProperty to %s") },

          // Swap children
          {
            val childrenList = children.toList()
            if (childrenList.size > 2) {
              val index1 = env.generateValue(Generator.integers(0, childrenList.lastIndex), null)
              val index2 = env.generateValue(Generator.integers(0, childrenList.lastIndex), null)
              env.logMessage(
                "Change children. Swap 2 elements: idx1: $index1, idx2: $index2, value1: ${childrenList[index1]}, value2: ${childrenList[index2]}")
              children = children.toMutableList().also { it.swap(index1, index2) }.asSequence()
            }
          },

          // Modify nullable children
          {
            val removeValue = env.generateValue(Generator.booleans(), null)
            if (removeValue) {
              if (optionalChildren.any()) {
                val childrenList = optionalChildren.toMutableList()
                val i = env.generateValue(Generator.integers(0, childrenList.lastIndex), null)
                env.logMessage("Remove item from optionalChildren. Index: $i, Element ${childrenList[i]}")
                childrenList.removeAt(i)
                optionalChildren = childrenList.asSequence()
              }
            }
            else {
              // TODO: 04.06.2020 Add children adding
            }
          }
        ), null)

        storage.modifyEntity(ModifiableParentEntity::class.java, entity, modification)
        return true
      }
    }
  }

  private fun <A> MutableList<A>.swap(index1: Int, index2: Int) {
    val tmp = this[index1]
    this[index1] = this[index2]
    this[index2] = tmp
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

  override fun modifyManipulation(storage: WorkspaceEntityStorageBuilderImpl): ModifyEntity<SampleEntity> {
    return object : ModifyEntity<SampleEntity>(storage, "Sample", SampleEntity::class.java.toClassId()) {
      override fun modifyEntity(entity: SampleEntity, someProperty: String, env: ImperativeCommand.Environment): Boolean {

        // Select modification
        val modification = env.generateValue(Generator.sampledFrom<ModifiableSampleEntity.() -> Unit>(

          // Change booleanProperty
          { booleanProperty = env.generateValue(Generator.booleans(), "Change booleanProperty to %s") },

          // Change stringProperty
          { stringProperty = env.generateValue(randomNames, "Change stringProperty to %s") },

          // Change stringListProperty
          {
            val removeValue = env.generateValue(Generator.booleans(), null)
            if (removeValue) {
              if (stringListProperty.isNotEmpty()) {
                val i = env.generateValue(Generator.integers(0, stringListProperty.lastIndex), null)
                env.logMessage("Remove item from stringListProperty. Index: $i, Element ${stringListProperty[i]}")
                stringListProperty = stringListProperty.toMutableList().also { it.removeAt(i) }
              }
            }
            else {
              val newElement = env.generateValue(randomNames, "Adding new element to stringListProperty: %s")
              stringListProperty = stringListProperty + newElement
            }
          }
        ), null)

        storage.modifyEntity(ModifiableSampleEntity::class.java, entity, modification)
        return true
      }
    }
  }
}

