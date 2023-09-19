// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.impl.serialization.EntityStorageSerializerImpl
import com.intellij.platform.workspace.storage.impl.MatchedEntitySource
import com.intellij.platform.workspace.storage.impl.SimpleEntityTypesResolver
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.toBuilder
import java.io.File
import kotlin.system.exitProcess

/**
 * This is a boilerplate code for analyzing state of entity stores that were received as attachments to exceptions
 */
fun main(args: Array<String>) {
  if (args.size != 1) {
    println("Usage: com.intellij.workspaceModel.ide.StoreSnapshotsAnalyzerKt <path to directory with storage files>")
    exitProcess(1)
  }
  val file = File(args[0])
  if (!file.exists()) {
    throw IllegalArgumentException("$file doesn't exist")
  }

  if (file.isFile) {
    val serializer = EntityStorageSerializerImpl(SimpleEntityTypesResolver, VirtualFileUrlManagerImpl())
    val storage = serializer.deserializeCache(file.toPath()).getOrThrow()

    // Set a breakpoint and check
    println("Cache loaded: ${storage!!.entities(ModuleEntity::class.java).toList().size} modules")
    return
  }

  val leftFile = file.resolve("Left_Store")
  val rightFile = file.resolve("Right_Store")
  val rightDiffLogFile = file.resolve("Right_Diff_Log")
  val converterFile = file.resolve("ClassToIntConverter")
  val resFile = file.resolve("Res_Store")

  val serializer = EntityStorageSerializerImpl(SimpleEntityTypesResolver, VirtualFileUrlManagerImpl())

  serializer.deserializeClassToIntConverter(converterFile.toPath())

  val resStore = serializer.deserializeCache(resFile.toPath()).getOrThrow()!!

  val leftStore = serializer.deserializeCache(leftFile.toPath()).getOrThrow() ?: throw IllegalArgumentException("Cannot load cache")

  if (file.resolve("Replace_By_Source").exists()) {
    val rightStore = serializer.deserializeCache(rightFile.toPath()).getOrThrow()!!

    val allEntitySources = leftStore.entitiesBySource { true }.map { it.key }.toHashSet()
    allEntitySources.addAll(rightStore.entitiesBySource { true }.map { it.key })

    val pattern = if (file.resolve("Report_Wrapped").exists()) {
      matchedPattern()
    }
    else {
      val sortedSources = allEntitySources.sortedBy { it.toString() }
      patternFilter(file.resolve("Replace_By_Source").readText(), sortedSources)
    }

    val expectedResult = leftStore.toSnapshot().toBuilder()
    expectedResult.replaceBySource(pattern, rightStore)

    // Set a breakpoint and check
    println("storage loaded")
  }
  else {
    val rightStore = serializer.deserializeCacheAndDiffLog(rightFile.toPath(), rightDiffLogFile.toPath())!!

    val expectedResult = leftStore.toSnapshot().toBuilder()
    expectedResult.addDiff(rightStore)

    // Set a breakpoint and check
    println("storage loaded")
  }
}

fun patternFilter(pattern: String, sortedSources: List<EntitySource>): (EntitySource) -> Boolean {
  return {
    val idx = sortedSources.indexOf(it)
    pattern[idx] == '1'
  }
}

fun matchedPattern(): (EntitySource) -> Boolean {
  return {
    it is MatchedEntitySource
  }
}
