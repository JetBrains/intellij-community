// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl
import com.intellij.workspaceModel.storage.impl.SimpleEntityTypesResolver
import com.intellij.workspaceModel.storage.toBuilder
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import java.io.File
import kotlin.system.exitProcess

/**
 * This is a boilerplate code for analyzing state of entity stores that were received as attachments to exceptions
 */
fun main(args: Array<String>) {
  if (args.size !in 1..2) {
    println("Usage: com.intellij.workspaceModel.ide.StoreSnapshotsAnalyzerKt <path to directory with storage files> [<entity source filter>]")
    exitProcess(1)
  }
  val dir = File(args[0])
  if (!dir.exists()) {
    throw IllegalArgumentException("$dir doesn't exist")
  }
  val leftFile = dir.resolve("Left_Store")
  val rightFile = dir.resolve("Right_Store")
  val rightDiffLogFile = dir.resolve("Right_Diff_Log")
  val converterFile = dir.resolve("ClassToIntConverter")
  val resFile = dir.resolve("Res_Store")

  val serializer = EntityStorageSerializerImpl(SimpleEntityTypesResolver, VirtualFileUrlManagerImpl())

  serializer.deserializeClassToIntConverter(converterFile.inputStream())

  val leftStore = serializer.deserializeCache(leftFile.inputStream()) ?: throw IllegalArgumentException("Cannot load cache")
  val filterPattern = args.getOrNull(1)
  if (filterPattern != null) {
    val rightStore = serializer.deserializeCache(rightFile.inputStream())!!
    val resStore = serializer.deserializeCache(resFile.inputStream())!!

    val allEntitySources = leftStore.entitiesBySource { true }.map { it.key }.toHashSet()
    allEntitySources.addAll(rightStore.entitiesBySource { true }.map { it.key })
    val sortedSources = allEntitySources.sortedBy { it.toString() }

    val expectedResult = leftStore.toBuilder()
    expectedResult.replaceBySource(patternFilter(filterPattern, sortedSources), rightStore)

    // Set a breakpoint and check
    println("storage loaded")
  }
  else {
    val rightStore = serializer.deserializeCacheAndDiffLog(rightFile.inputStream(), rightDiffLogFile.inputStream())!!
    val resStore = serializer.deserializeCache(resFile.inputStream())!!

    val expectedResult = leftStore.toBuilder()
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
