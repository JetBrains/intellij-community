
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.module

import com.intellij.ide.projectView.impl.ModuleGroup
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleGrouper
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.HeavyPlatformTestCase
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ExplicitModuleGroupTest : HeavyPlatformTestCase() {
  @Test
  fun `test single module`() {
    val module = createModuleInGroup("module", "a", "b")
    val path = ModuleManager.getInstance(myProject).getModuleGroupPath(module)!!
    assertArrayEquals(path, arrayOf("a", "b"))

    val parentGroup = ModuleGroup(listOf("a"))
    assertEmpty(parentGroup.modulesInGroup(myProject, false))
    assertEmpty(parentGroup.modulesInGroup(myProject))
    assertSameElements(parentGroup.modulesInGroup(myProject, true), module)

    val group = assertOneElement(parentGroup.childGroups(grouper))
    assertArrayEquals(group.groupPath, arrayOf("a", "b"))
    assertSameElements(group.modulesInGroup(grouper, false), module)
    assertSameElements(group.modulesInGroup(myProject), module)
    assertEmpty(group.childGroups(grouper))
    assertEmpty(group.childGroups(myProject))
  }

  fun `test two modules`() {
    val module1 = createModuleInGroup("module1", "a")
    val module2 = createModuleInGroup("module2", "a", "b")

    val parentGroup = ModuleGroup(listOf("a"))
    assertSameElements(parentGroup.modulesInGroup(myProject, false), module1)
    assertSameElements(parentGroup.modulesInGroup(myProject), module1)
    assertSameElements(parentGroup.modulesInGroup(myProject, true), module1, module2)

    assertArrayEquals(assertOneElement(parentGroup.childGroups(myProject)).groupPath, arrayOf("a", "b"))
    val group = assertOneElement(parentGroup.childGroups(grouper))
    assertArrayEquals(group.groupPath, arrayOf("a", "b"))
    assertSameElements(group.modulesInGroup(myProject, false), module2)
    assertSameElements(group.modulesInGroup(myProject), module2)
    assertEmpty(group.childGroups(grouper))
    assertEmpty(group.childGroups(myProject))
  }

  private val grouper: ModuleGrouper
    get() = ModuleGrouper.instanceFor(myProject)

  private fun createModuleInGroup(name: String, vararg path: String): Module {
    val module = createModule(name)
    val model = ModuleManager.getInstance(myProject).getModifiableModel()
    model.setModuleGroupPath(module, path)
    runWriteAction { model.commit() }
    return module
  }
}