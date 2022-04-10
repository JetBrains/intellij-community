// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.propertyBased

import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.impl.ClassToIntConverter
import com.intellij.workspaceModel.storage.impl.EntityId
import com.intellij.workspaceModel.storage.impl.createEntityId
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

class VirtualFileIndexTest {
  @Rule
  @JvmField
  var application = ApplicationRule()
  
  @Rule
  @JvmField
  var temporaryDirectoryRule = TemporaryDirectory()

  @Rule
  @JvmField
  var disposableRule = DisposableRule()
  
  val manager = VirtualFileUrlManagerImpl()
  
  @Test
  fun `property test`() {
    PropertyChecker.checkScenarios {
      ImperativeCommand { env ->
        val index = VirtualFileIndex.MutableVirtualFileIndex.from(VirtualFileIndex())

        val immutables = ArrayList<VirtualFileIndex>()

        env.executeCommands(Generator.sampledFrom(
          AddValue(index),
          AddEmpty(index),
          RemoveValue(index),
          RemoveByIdValue(index),
          ToImmutable(index, immutables),
        ))
      }
    }
  }
  
  private inner class AddValue(private val index: VirtualFileIndex.MutableVirtualFileIndex) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val (pointer, id, prop) = generateData(env)

      val files = setOf(pointer).toMutableSet()
      val moreFiles = env.generateValue(Generator.integers(0, 10), null)
      repeat(moreFiles) {
        files.add(env.generateValue(vfuGenerator, null))
      }

      index.index(id, prop, files)
      env.logMessage("Add pointer")
      index.assertConsistency()
    }
  }

  private inner class RemoveValue(private val index: VirtualFileIndex.MutableVirtualFileIndex) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val (pointer, id, prop) = generateData(env)
      index.index(id, prop, null)
      env.logMessage("Remove pointer")
      index.assertConsistency()
    }
  }

  private inner class AddEmpty(private val index: VirtualFileIndex.MutableVirtualFileIndex) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val (pointer, id, prop) = generateData(env)
      index.index(id, prop, emptySet())
      env.logMessage("Remove pointer")
      index.assertConsistency()
    }
  }

  private inner class RemoveByIdValue(private val index: VirtualFileIndex.MutableVirtualFileIndex) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val (pointer, id, prop) = generateData(env)
      index.removeRecordsByEntityId(id)
      env.logMessage("Remove by id")
      index.assertConsistency()
    }
  }

  private inner class ToImmutable(private val index: VirtualFileIndex.MutableVirtualFileIndex, private val immutables: MutableList<VirtualFileIndex>) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      immutables.forEach {
        it.assertConsistency()
      }
      val immutable = index.toImmutable()
      env.logMessage("ToImmutable")
      index.assertConsistency()
      immutable.assertConsistency()
      immutables += immutable
    }
  }

  private fun generateData(env: ImperativeCommand.Environment): Triple<VirtualFileUrl, EntityId, String> {
    val pointer = env.generateValue(vfuGenerator, null)
    val id = env.generateValue(entityIdGenerator, null)
    val property = env.generateValue(propertyGenerator, null)
    return Triple(pointer, id, property)
  }

  private var existingPaths = ArrayList<Path>()

  internal val vfuGenerator: Generator<VirtualFileUrl> = Generator.from { env ->
    val select = env.generate(Generator.integers(0, 10))
    val file = if (select == 0 && existingPaths.isNotEmpty()) {
      env.generate(Generator.sampledFrom(existingPaths))
    }
    else {
      val temp = temporaryDirectoryRule.newPath()
      existingPaths.add(temp)
      if (existingPaths.size > 30) {
        existingPaths.removeFirst()
      }
      temp
    }

    manager.fromPath(file.toString())
  }
  
  internal val entityIdGenerator = Generator.from { env ->
    val clazz: Class<out WorkspaceEntity> = env.generate<Class<out WorkspaceEntity>>(Generator.sampledFrom(
      ModuleEntity::class.java,
      ContentRootEntity::class.java,
      SourceRootEntity::class.java,
      FacetEntity::class.java,
      ArtifactEntity::class.java,
    ))
    val id = env.generate(Generator.integers(0, 100))
    
    createEntityId(id, ClassToIntConverter.INSTANCE.getInt(clazz))
  }

  internal val propertyGenerator = Generator.from { env ->
    env.generate(Generator.sampledFrom(
      "One",
      "Two",
      "Three",
      "Four",
      "Five",
      "Six",
      "Seven",
    ))
  }
}

