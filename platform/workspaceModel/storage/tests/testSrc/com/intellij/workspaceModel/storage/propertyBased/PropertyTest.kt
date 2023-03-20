// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.propertyBased

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.entities.test.api.AnotherSource
import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.impl.MutableEntityStorageImpl
import com.intellij.workspaceModel.storage.impl.RefsTable
import com.intellij.workspaceModel.storage.impl.StorageIndexes
import com.intellij.workspaceModel.storage.impl.assertConsistency
import com.intellij.workspaceModel.storage.impl.exceptions.AddDiffException
import com.intellij.workspaceModel.storage.impl.exceptions.ReplaceBySourceException
import junit.framework.TestCase
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import org.junit.Test
import kotlin.reflect.full.memberProperties

class PropertyTest {

  @Test
  fun entityManipulations() {
    PropertyChecker.checkScenarios {
      ImperativeCommand { env ->
        val workspace = env.generateValue(newEmptyWorkspace, "Generate empty workspace")
        val detachedEntities = ArrayList<WorkspaceEntity>()
        env.executeCommands(getEntityManipulation(workspace, detachedEntities))
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

  @Test
  fun testAddDiff() {
    PropertyChecker.checkScenarios {
      ImperativeCommand { env ->
        val workspace = env.generateValue(newEmptyWorkspace, "Generate empty workspace")
        env.executeCommands(AddDiff.create(workspace))
        workspace.assertConsistency()
      }
    }
  }
}

private class AddDiff(private val storage: MutableEntityStorage) : ImperativeCommand {
  override fun performCommand(env: ImperativeCommand.Environment) {
    env.logMessage("Trying to perform addDiff")
    val backup = storage.toSnapshot()
    val another = createBuilderFrom(backup)
    env.logMessage("Modify diff:")
    env.executeCommands(getEntityManipulation(another))

    /*
    // Do not modify local store currently
    env.logMessage("Modify original storage:")
    env.executeCommands(getEntityManipulation(storage as WorkspaceEntityStorageBuilderImpl))
    */

    try {
      storage.addDiff(another)
      env.logMessage("addDiff finished")
      env.logMessage("---------------------------")
    }
    catch (e: AddDiffException) {
      env.logMessage("Cannot perform addDiff: ${e.message}. Fallback to previous state")
      (storage as MutableEntityStorageImpl).restoreFromBackup(backup)
    }
  }

  companion object {
    fun create(workspace: MutableEntityStorage): Generator<AddDiff> = Generator.constant(AddDiff(workspace))
  }
}

private class ReplaceBySource(private val storage: MutableEntityStorage) : ImperativeCommand {
  override fun performCommand(env: ImperativeCommand.Environment) {
    env.logMessage("Trying to perform replaceBySource")
    val backup = storage.toSnapshot()
    val another = createBuilderFrom(backup)
    env.logMessage("Modify original storage:")
    env.executeCommands(getEntityManipulation(another))

    val filter = env.generateValue(filter, null)
    env.logMessage("Create filter for replaceBySource: ${filter.second}")

    try {
      storage.replaceBySource(filter.first, another)
    }
    catch (e: AssertionError) {
      if (e.cause !is ReplaceBySourceException) error("ReplaceBySource exception expected")
      env.logMessage("Cannot perform replace by source: ${e.message}. Fallback to previous state")
      (storage as MutableEntityStorageImpl).restoreFromBackup(backup)
    }
  }

  companion object {
    val filter: Generator<Pair<(EntitySource) -> Boolean, String>> = Generator.sampledFrom(
      { _: EntitySource -> true } to "Always true",
      { _: EntitySource -> false } to "Always false",
      { it: EntitySource -> it is MySource } to "MySource filter",
      { it: EntitySource -> it is AnotherSource } to "AnotherSource filter"
    )

    fun create(workspace: MutableEntityStorage): Generator<ReplaceBySource> = Generator.constant(ReplaceBySource(workspace))
  }
}

private fun MutableEntityStorageImpl.restoreFromBackup(backup: EntityStorage) {
  val backupBuilder = createBuilderFrom(backup)
  entitiesByType.entityFamilies.clear()
  entitiesByType.entityFamilies.addAll(backupBuilder.entitiesByType.entityFamilies)

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
  indexes.virtualFileIndex.clear()
  indexes.entitySourceIndex.clear()
  indexes.symbolicIdIndex.clear()
  indexes.externalMappings.clear()

  indexes.softLinks.copyFrom(backupBuilder.indexes.softLinks)
  indexes.virtualFileIndex.copyFrom(backupBuilder.indexes.virtualFileIndex)
  indexes.entitySourceIndex.copyFrom(backupBuilder.indexes.entitySourceIndex)
  indexes.symbolicIdIndex.copyFrom(backupBuilder.indexes.symbolicIdIndex)
  indexes.externalMappings.putAll(indexes.externalMappings)
  // Just checking that all properties have been asserted
  TestCase.assertEquals(5, StorageIndexes::class.memberProperties.size)
}
