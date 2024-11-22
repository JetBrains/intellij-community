// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleAssertions")

package com.intellij.testFramework.utils.module

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertContains
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.entities

fun assertModules(project: Project, vararg expectedNames: String) {
  assertModules(project, expectedNames.asList())
}

fun assertModules(project: Project, expectedNames: List<String>) {
  val storage = project.workspaceModel.currentSnapshot
  assertModules(storage, expectedNames)
}

fun assertModules(storage: EntityStorage, vararg expectedNames: String) {
  assertModules(storage, expectedNames.asList())
}

fun assertModules(storage: EntityStorage, expectedNames: List<String>) {
  val actualNames = storage.entities<ModuleEntity>().map { it.name }.toList()
  assertEqualsUnordered(expectedNames, actualNames)
}

fun assertModulesContains(project: Project, vararg expectedNames: String) {
  assertModulesContains(project, expectedNames.asList())
}

fun assertModulesContains(project: Project, expectedNames: List<String>) {
  val storage = project.workspaceModel.currentSnapshot
  assertModulesContains(storage, expectedNames)
}

fun assertModulesContains(storage: EntityStorage, vararg expectedNames: String) {
  assertModulesContains(storage, expectedNames.asList())
}

fun assertModulesContains(storage: EntityStorage, expectedNames: List<String>) {
  val actualNames = storage.entities<ModuleEntity>().map { it.name }.toList()
  assertContains(expectedNames, actualNames)
}