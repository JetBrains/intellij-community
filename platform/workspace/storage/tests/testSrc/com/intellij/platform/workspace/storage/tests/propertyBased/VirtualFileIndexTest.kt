// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.tests.propertyBased

import com.intellij.platform.workspace.storage.impl.EntityId
import com.intellij.platform.workspace.storage.impl.createEntityId
import com.intellij.platform.workspace.storage.impl.indices.VirtualFileIndex
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class VirtualFileIndexTest {
  @TempDir
  lateinit var tempdir: Path

  private val manager = VirtualFileUrlManagerImpl()

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
      val (_, id, prop) = generateData(env)
      index.index(id, prop, null)
      env.logMessage("Remove pointer")
      index.assertConsistency()
    }
  }

  private inner class AddEmpty(private val index: VirtualFileIndex.MutableVirtualFileIndex) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val (_, id, prop) = generateData(env)
      index.index(id, prop, emptySet())
      env.logMessage("Remove pointer")
      index.assertConsistency()
    }
  }

  private inner class RemoveByIdValue(private val index: VirtualFileIndex.MutableVirtualFileIndex) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val (_, id, _) = generateData(env)
      index.removeRecordsByEntityId(id)
      env.logMessage("Remove by id")
      index.assertConsistency()
    }
  }

  private inner class ToImmutable(private val index: VirtualFileIndex.MutableVirtualFileIndex,
                                  private val immutables: MutableList<VirtualFileIndex>) : ImperativeCommand {
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
      existingPaths.add(tempdir)
      if (existingPaths.size > 30) {
        existingPaths.removeFirst()
      }
      tempdir
    }

    file.toVirtualFileUrl(manager)
  }

  private val entityIdGenerator = Generator.from { env ->
    val clazzId = env.generate(Generator.integers(0, 5))
    val id = env.generate(Generator.integers(0, 100))

    createEntityId(id, clazzId)
  }

  private val propertyGenerator = Generator.from { env ->
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

