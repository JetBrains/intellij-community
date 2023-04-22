// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleAssertions")
package com.intellij.testFramework.utils.module

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import org.junit.jupiter.api.Assertions


fun assertModules(project: Project, vararg expectedNames: String) {
  assertModules(project, expectedNames.asIterable())
}

fun assertModules(project: Project, expectedNames: Iterable<String>) {
  val actualNames = project.modules.map { it.name }
  Assertions.assertEquals(expectedNames.toSet(), actualNames.toSet())
}