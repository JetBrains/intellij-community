// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.propertyBased

import com.google.common.collect.HashBiMap
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.*
import com.intellij.platform.workspace.storage.impl.StorageIndexes
import com.intellij.platform.workspace.storage.impl.exceptions.ApplyChangesFromException
import com.intellij.platform.workspace.storage.impl.exceptions.ReplaceBySourceException
import com.intellij.platform.workspace.storage.testEntities.entities.AnotherSource
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.tests.createBuilderFrom
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals

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
  fun testApplyChangesFrom() {
    PropertyChecker.checkScenarios {
      ImperativeCommand { env ->
        val workspace = env.generateValue(newEmptyWorkspace, "Generate empty workspace")
        env.executeCommands(ApplyChangesFrom.create(workspace))
        workspace.assertConsistency()
      }
    }
  }

  /**
   * The targer builder is created every iteration
   */
  @Test
  fun `add diff generates same changelog simple test`() {
    PropertyChecker.checkScenarios {
      ImperativeCommand { env ->
        env.executeCommands(ApplyChangesFromCheckChangelog.create(null))
      }
    }
  }

  /**
   * This test uses a single target builder that changes every iteration and reused in all iterations
   */
  @Test
  fun `add diff generates same changelog`() {
    PropertyChecker.checkScenarios {
      ImperativeCommand { env ->
        val workspace = env.generateValue(newEmptyWorkspace, "Generate empty workspace")
        env.executeCommands(ApplyChangesFromCheckChangelog.create(workspace))
        workspace.assertConsistency()
      }
    }
  }
}

private class ApplyChangesFrom(private val storage: MutableEntityStorage) : ImperativeCommand {
  override fun performCommand(env: ImperativeCommand.Environment) {
    env.logMessage("Trying to perform applyChangesFrom")
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
      storage.applyChangesFrom(another)
      env.logMessage("applyChangesFrom finished")
      env.logMessage("---------------------------")
    }
    catch (e: ApplyChangesFromException) {
      env.logMessage("Cannot perform applyChangesFrom: ${e.message}. Fallback to previous state")
      (storage as MutableEntityStorageImpl).restoreFromBackup(backup)
    }
  }

  companion object {
    fun create(workspace: MutableEntityStorage): Generator<ApplyChangesFrom> = Generator.constant(ApplyChangesFrom(workspace))
  }
}

private class ApplyChangesFromCheckChangelog(val preBuilder: MutableEntityStorageImpl?) : ImperativeCommand {
  override fun performCommand(env: ImperativeCommand.Environment) {
    env.logMessage("Trying to perform applyChangesFrom")
    val storage = preBuilder ?: run {
      val storage = createEmptyBuilder()
      env.logMessage("Prepare empty builder:")
      env.executeCommands(getEntityManipulation(storage))
      val updatedBuilder = storage.toSnapshot().toBuilder() as MutableEntityStorageImpl
      updatedBuilder
    }
    val another = storage.toSnapshot().toBuilder() as MutableEntityStorageImpl
    env.logMessage("Modify diff:")
    env.executeCommands(getEntityManipulation(another))

    try {
      var applyChangesFromEngineStolen: ApplyChanesFromOperation? = null
      storage.changeLog.clear()
      storage.upgradeApplyChangesFromEngine = { applyChangesFromEngineStolen = it }
      storage.applyChangesFrom(another)

      val actualChangelog = storage.changeLog.changeLog.let { HashMap(it) }

      // Since the target builder may have different ids, we take the changelog from diff and change all events to have the same IDs as in
      //   storage. We change ids in place, so this is a destructive operation for [another] builder.
      val updatedChangelog = updateWithReplaceMap(applyChangesFromEngineStolen!!.replaceMap, another.changeLog.changeLog.let { HashMap(it) })

      assertEquals(updatedChangelog, actualChangelog)

      env.logMessage("applyChangesFrom finished")
      env.logMessage("---------------------------")
    }
    catch (e: ApplyChangesFromException) {
      env.logMessage("Cannot perform applyChangesFrom: ${e.message}.")
    }
  }

  private fun updateWithReplaceMap(replaceMap: HashBiMap<NotThisEntityId, ThisEntityId>,
                                   expectedChangelog: HashMap<EntityId, ChangeEntry>): Map<EntityId, ChangeEntry> {
    return buildMap {
      expectedChangelog.forEach { (id, entry) ->
        when (entry) {
          is ChangeEntry.AddEntity -> {
            val replacement = replaceMap[id.notThis()]?.id
            if (replacement != null) {
              put(replacement, entry.copy(entityData = entry.entityData.also { it.id = replacement.arrayId }))
            }
            else {
              put(id, entry)
            }
          }
          is ChangeEntry.RemoveEntity -> {
            val replacement = replaceMap[id.notThis()]?.id
            if (replacement != null) {
              put(replacement, entry.copy(id = replacement))
            }
            else {
              put(id, entry)
            }
          }
          is ChangeEntry.ReplaceEntity -> {
            val replacement = replaceMap[id.notThis()]?.id
            var newId: EntityId = id
            var newEntry: ChangeEntry.ReplaceEntity = entry
            if (replacement != null) {
              newId = replacement
              newEntry = entry.copy(data = entry.data?.copy(newData = entry.data.newData.also { it.id = replacement.arrayId }))
            }
            newEntry = newEntry.copy(
              references = entry.references?.copy(
                newChildren = entry.references.newChildren.mapTo(HashSet()) {
                  it.copy(second = replaceMap[it.second.id.notThis()]?.id?.asChild() ?: it.second)
                },
                removedChildren = entry.references.removedChildren.mapTo(HashSet()) {
                  it.copy(second = replaceMap[it.second.id.notThis()]?.id?.asChild() ?: it.second)
                },
                newParents = entry.references.newParents.mapValues { (_, v) -> replaceMap[v.id.notThis()]?.id?.asParent() ?: v },
                removedParents = entry.references.removedParents.mapValues { (_, v) -> replaceMap[v.id.notThis()]?.id?.asParent() ?: v },
              )
            )
            put(newId, newEntry)
          }
        }
      }
    }
  }

  companion object {
    fun create(preBuilder: MutableEntityStorageImpl?): Generator<ApplyChangesFromCheckChangelog> = Generator.constant(ApplyChangesFromCheckChangelog(preBuilder))
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
  assertEquals(4, RefsTable::class.memberProperties.size)


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
  assertEquals(5, StorageIndexes::class.memberProperties.size)
}
