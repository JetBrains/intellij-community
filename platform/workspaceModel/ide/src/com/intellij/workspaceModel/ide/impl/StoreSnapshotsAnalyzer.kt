// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl
import com.intellij.workspaceModel.storage.impl.SimpleEntityTypesResolver
import com.intellij.workspaceModel.storage.impl.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.toBuilder
import java.io.File

/**
 * This is a boilerplate code for analyzing state of entity stores that were received as attachments to exceptions
 */
fun main1() {
  // Path to the file
  val path = "/Users/alex.plate/Storages"
  val dir = File(path)
  val leftFile = dir.resolve("Left_Store")
  val rightFile = dir.resolve("Right_Store")
  val resFile = dir.resolve("Res_Store")
  val filterPattern = "111"

  val serializer = EntityStorageSerializerImpl(SimpleEntityTypesResolver, VirtualFileUrlManagerImpl(), false)

  val leftStore = serializer.deserializeCache(leftFile.inputStream())!!
  val rightStore = serializer.deserializeCache(rightFile.inputStream())!!
  //val resStore = serializer.deserializeCache(resFile.inputStream())!!

  val allEntitySources = leftStore.entitiesBySource { true }.map { it.key }.toHashSet()
  allEntitySources.addAll(rightStore.entitiesBySource { true }.map { it.key })
  val sortedSources = allEntitySources.sortedBy { it.toString() }

  val expectedResult = leftStore.toBuilder()
  expectedResult.replaceBySource(patternFilter(filterPattern, sortedSources), rightStore)

  // Set a breakpoint and check

  println()
}

fun main() {
  // Path to the file
  val path = "/Users/alex.plate/Storages"
  val dir = File(path)
  val leftFile = dir.resolve("Left_Store")
  val rightFile = dir.resolve("Right_Store")
  val rightDiffLogFile = dir.resolve("Right_Diff_Log")
  val leftDiffLogFile = dir.resolve("Left_Diff_Log")
  val converterFile = dir.resolve("ClassToIntConverter")
  val resFile = dir.resolve("Res_Store")

  val serializer = EntityStorageSerializerImpl(SimpleEntityTypesResolver, VirtualFileUrlManagerImpl(), false)

  serializer.deserializeClassToIntConverter(converterFile.inputStream())

  val leftStore = serializer.deserializeCacheAndDiffLog(leftFile.inputStream(), leftDiffLogFile.inputStream())!!
  val rightStore = serializer.deserializeCacheAndDiffLog(rightFile.inputStream(), rightDiffLogFile.inputStream())!!
  //val resStore = serializer.deserializeCache(resFile.inputStream())!!

  val expectedResult = leftStore.toBuilder()
  expectedResult.addDiff(rightStore)

  // Set a breakpoint and check

  println()
}

fun patternFilter(pattern: String, sortedSources: List<EntitySource>): (EntitySource) -> Boolean {
  return {
    val idx = sortedSources.indexOf(it)
    pattern[idx] == '1'
  }
}
