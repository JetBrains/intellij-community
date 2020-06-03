// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.propertyBased

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.entities.*
import com.intellij.workspaceModel.storage.impl.EntityId
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.storage.impl.toClassId
import org.jetbrains.jetCheck.GenerationEnvironment
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Ignore
import org.junit.Test

class PropertyTest {

  @Test
  fun entityManipulations() {
    PropertyChecker.checkScenarios {
      ImperativeCommand { env ->
        val workspace = env.generateValue(newEmptyWorkspace, "Generate empty workspace")
        env.executeCommands(getEntityManipulation(workspace))
        workspace.assertConsistency()
      }
    }
  }

  // Keep this test ignored and empty.
  // This function is created for interactive debug sessions only.
  @Ignore
  @Test
  fun recheck() {
  }
}

private fun getEntityManipulation(workspace: WorkspaceEntityStorageBuilderImpl): Generator<ImperativeCommand>? {
  return Generator.anyOf(getRemoveGenerator(workspace), getAddGenerator(workspace))
}

private fun getAddGenerator(workspace: WorkspaceEntityStorageBuilderImpl): Generator<ImperativeCommand> {
  return Generator.sampledFrom(
    AddSampleEntity(workspace),
    AddParentEntity(workspace),
    AddChildEntity(workspace),
    AddChildWithOptionalParentEntity(workspace)
  )
}

private fun getRemoveGenerator(workspace: WorkspaceEntityStorageBuilderImpl) = Generator.sampledFrom(RemoveSomeEntity(workspace))

private val newEmptyWorkspace
  get() = Generator.constant(WorkspaceEntityStorageBuilderImpl.create())

private abstract class AddEntity(protected val storage: WorkspaceEntityStorageBuilderImpl,
                                 private val entityDescription: String) : ImperativeCommand {
  abstract fun makeEntity(source: EntitySource, someProperty: String, env: ImperativeCommand.Environment): WorkspaceEntity?
  final override fun performCommand(env: ImperativeCommand.Environment) {
    env.logMessage("Trying to add $entityDescription entity")
    val property = env.generateValue(randomNames, null)
    val source = env.generateValue(sources, null)
    val createdEntity = makeEntity(source, property, env) as? WorkspaceEntityBase
    if (createdEntity != null) {
      assertNotNull(storage.entityDataById(createdEntity.id))
      env.logMessage("New entity added: $createdEntity. Source: ${createdEntity.entitySource}")
      env.logMessage("--------------------------------")
    }
  }
}

private class AddChildWithOptionalParentEntity(storage: WorkspaceEntityStorageBuilderImpl) : AddEntity(storage,
                                                                                                       "ChildWithOptionalDependency") {
  override fun makeEntity(source: EntitySource, someProperty: String, env: ImperativeCommand.Environment): WorkspaceEntity? {
    val classId = ParentEntity::class.java.toClassId()
    val parentId = env.generateValue(EntityIdOfFamilyGenerator.create(storage, classId), "Select parent for child: %s")
    val parentEntity = parentId?.let { storage.entityDataByIdOrDie(it).createEntity(storage) as ParentEntity }
    return storage.addChildWithOptionalParentEntity(parentEntity, someProperty, source)
  }
}

private class AddChildEntity(storage: WorkspaceEntityStorageBuilderImpl) : AddEntity(storage, "Child") {
  override fun makeEntity(source: EntitySource, someProperty: String, env: ImperativeCommand.Environment): WorkspaceEntity? {
    val classId = ParentEntity::class.java.toClassId()
    val parentId = env.generateValue(EntityIdOfFamilyGenerator.create(storage, classId), "Select parent for child: %s") ?: return null
    return storage.addChildEntity(storage.entityDataByIdOrDie(parentId).createEntity(storage) as ParentEntity, someProperty, null, source)
  }
}

private class AddParentEntity(storage: WorkspaceEntityStorageBuilderImpl) : AddEntity(storage, "Parent") {
  override fun makeEntity(source: EntitySource, someProperty: String, env: ImperativeCommand.Environment): WorkspaceEntity? {
    return storage.addParentEntity(someProperty, source)
  }
}

private class AddSampleEntity(storage: WorkspaceEntityStorageBuilderImpl) : AddEntity(storage, "Sample") {
  override fun makeEntity(source: EntitySource, someProperty: String, env: ImperativeCommand.Environment): WorkspaceEntity? {
    return storage.addSampleEntity(someProperty, source)
  }
}

private class RemoveSomeEntity(private val storage: WorkspaceEntityStorageBuilderImpl) : ImperativeCommand {
  override fun performCommand(env: ImperativeCommand.Environment) {
    env.logMessage("Trying to remove random entity")
    val id = env.generateValue(WorkspaceIdGenerator.create(storage), "Generate random EntityId: %s") ?: return
    storage.removeEntity(storage.entityDataByIdOrDie(id).createEntity(storage))
    assertNull(storage.entityDataById(id))
    env.logMessage("Entity removed -------------------------")
  }
}

private class WorkspaceIdGenerator(private val storage: WorkspaceEntityStorageBuilderImpl) : java.util.function.Function<GenerationEnvironment, EntityId?> {
  override fun apply(t: GenerationEnvironment): EntityId? {
    val filtered = storage.entitiesByType.entities.asSequence().filterNotNull().flatMap { it.entities.filterNotNull().asSequence() }
    if (filtered.none()) return null
    return filtered.toList().random().createPid()
  }

  companion object {
    fun create(storage: WorkspaceEntityStorageBuilderImpl) = Generator.from(WorkspaceIdGenerator(storage))
  }
}

private class EntityIdOfFamilyGenerator(private val storage: WorkspaceEntityStorageBuilderImpl,
                                        private val family: Int) : java.util.function.Function<GenerationEnvironment, EntityId?> {
  override fun apply(t: GenerationEnvironment): EntityId? {
    val entityFamily = storage.entitiesByType.entities.getOrNull(family) ?: return null
    val existingEntities = entityFamily.entities.filterNotNull()
    if (existingEntities.isEmpty()) return null
    return existingEntities.random().createPid()
  }

  companion object {
    fun create(storage: WorkspaceEntityStorageBuilderImpl, family: Int) = Generator.from(EntityIdOfFamilyGenerator(storage, family))
  }
}

private val sources = Generator.anyOf(
  Generator.constant(MySource),
  Generator.constant(AnotherSource),
  Generator.from { SampleEntitySource(it.generate(randomNames)) }
)

private val randomNames = Generator.sampledFrom(
  "education",
  "health",
  "singer",
  "diamond",
  "energy",
  "imagination",
  "suggestion",
  "vehicle",
  "marriage",
  "people",
  "revolution",
  "decision",
  "homework",
  "heart",
  "candidate",
  "appearance",
  "poem",
  "outcome",
  "shopping",
  "government",
  "dinner",
  "unit",
  "quantity",
  "construction",
  "assumption",
  "development",
  "understanding",
  "committee",
  "complaint",
  "bedroom",
  "collection",
  "administration",
  "college",
  "addition",
  "height",
  "university",
  "map",
  "requirement",
  "people",
  "expression",
  "statement",
  "alcohol",
  "resolution",
  "charity",
  "opinion",
  "king",
  "wedding",
  "heart",
  "basis",
  "chemistry",
  "opportunity",
  "selection",
  "passenger",
  "teacher",
  "driver",
  "attitude",
  "presentation",
  "philosophy",
  "poetry",
  "significance",
  "editor",
  "examination",
  "buyer",
  "baseball",
  "disaster",
  "reflection",
  "dad",
  "activity",
  "instance",
  "idea"
)
