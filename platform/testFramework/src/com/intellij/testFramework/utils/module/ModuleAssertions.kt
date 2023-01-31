// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.module

import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import junit.framework.TestCase

fun assertModules(project: Project, vararg expectedNames: String) {
  val actual = getInstance(project).modules
  val actualNames: Collection<String> = actual.map { it.name }
  TestCase.assertEquals(ContainerUtil.newHashSet(*expectedNames), HashSet(actualNames))
}