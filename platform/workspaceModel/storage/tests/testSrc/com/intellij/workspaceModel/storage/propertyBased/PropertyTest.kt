// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.propertyBased

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.entities.*
import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.storage.impl.containers.putAll
import com.intellij.workspaceModel.storage.impl.exceptions.ReplaceBySourceException
import junit.framework.TestCase
import org.jetbrains.jetCheck.GenerationEnvironment
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Ignore
import org.junit.Test
import kotlin.reflect.full.memberProperties

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

  @Test
  fun testReplaceBySource() {
    PropertyChecker.checkScenarios {
      ImperativeCommand { env ->
        val workspace = env.generateValue(newEmptyWorkspace, "Generate empty workspace")
        env.executeCommands(ReplaceBySource.create(workspace))
        workspace.assertConsistency()
      }
    }
  }

  // Keep this test ignored and empty.
  // This function is created for interactive debug sessions only.
  @Ignore
  @Test
  fun recheck() {
    //PropertyChecker.customized()
    //  .rechecking("7tzWpx7qpL2LCh8DAQEBCgAAAQECPAEAAwEAAAED")
  }
}

private class ReplaceBySource(private val storage: WorkspaceEntityStorageBuilder) : ImperativeCommand {
  override fun performCommand(env: ImperativeCommand.Environment) {
    env.logMessage("Trying to perform replaceBySource")
    val backup = storage.toStorage()
    val another = WorkspaceEntityStorageBuilderImpl.from(backup)
    env.logMessage("Modify original storage:")
    env.executeCommands(getEntityManipulation(another))

    val filter = env.generateValue(filter, null)
    env.logMessage("Create filter for replaceBySource: ${filter.second}")

    try {
      storage.replaceBySource(filter.first, another)
    } catch (e: ReplaceBySourceException) {
      env.logMessage("Cannot perform replace by source: $e. Fallback to previous state")
      (storage as WorkspaceEntityStorageBuilderImpl).restoreFromBackup(backup)
    }
  }

  private fun WorkspaceEntityStorageBuilderImpl.restoreFromBackup(backup: WorkspaceEntityStorage) {
    val backupBuilder = WorkspaceEntityStorageBuilderImpl.from(backup)
    entitiesByType.entities.clear()
    entitiesByType.entities.addAll(backupBuilder.entitiesByType.entities)

    refs.oneToManyContainer.clear()
    refs.oneToOneContainer.clear()
    refs.oneToAbstractManyContainer.clear()
    refs.abstractOneToOneContainer.clear()

    refs.oneToManyContainer.putAll(backupBuilder.refs.oneToManyContainer)
    refs.oneToOneContainer.putAll(backupBuilder.refs.oneToOneContainer)
    refs.oneToAbstractManyContainer.putAll(backupBuilder.refs.oneToAbstractManyContainer)
    refs.abstractOneToOneContainer.putAll(backupBuilder.refs.abstractOneToOneContainer)
    // Just checking that all properties have been asserted
    TestCase.assertEquals(4, RefsTable::class.memberProperties.size)


    indexes.softLinks.clear()
    indexes.virtualFileIndex.index.clear()
    indexes.entitySourceIndex.index.clear()
    indexes.persistentIdIndex.index.clear()
    indexes.externalIndices.clear()

    indexes.softLinks.putAll(backupBuilder.indexes.softLinks)
    indexes.virtualFileIndex.index.putAll(backupBuilder.indexes.virtualFileIndex.index)
    indexes.entitySourceIndex.index.putAll(backupBuilder.indexes.entitySourceIndex.index)
    indexes.persistentIdIndex.index.putAll(backupBuilder.indexes.persistentIdIndex.index)
    indexes.externalIndices.putAll(indexes.externalIndices)
    // Just checking that all properties have been asserted
    TestCase.assertEquals(5, StorageIndexes::class.memberProperties.size)
  }

  companion object {
    val filter: Generator<Pair<(EntitySource) -> Boolean, String>> = Generator.sampledFrom(
      { _: EntitySource -> true } to "Always true",
      { _: EntitySource -> false } to "Always false",
      { it: EntitySource -> it is MySource } to "MySource filter",
      { it: EntitySource -> it is AnotherSource } to "AnotherSource filter"
    )

    fun create(workspace: WorkspaceEntityStorageBuilder): Generator<ReplaceBySource> = Generator.constant(ReplaceBySource(workspace))
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
    env.logMessage("Entity removed")
    env.logMessage("-------------------------")
  }
}

private class WorkspaceIdGenerator(private val storage: WorkspaceEntityStorageBuilderImpl) : java.util.function.Function<GenerationEnvironment, EntityId?> {
  override fun apply(t: GenerationEnvironment): EntityId? {
    val filtered = storage.entitiesByType.entities.asSequence().filterNotNull().flatMap { it.entities.filterNotNull().asSequence() }
    if (filtered.none()) return null
    val nonNullIds = filtered.toList()
    val id = t.generate(Generator.integers(0, nonNullIds.lastIndex))
    return nonNullIds[id].createPid()
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
    val randomId = t.generate(Generator.integers(0, existingEntities.lastIndex))
    return existingEntities[randomId].createPid()
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
