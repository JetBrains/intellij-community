
/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.module

import com.intellij.ide.projectView.impl.ModuleGroup
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleGrouper
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.PlatformTestCase
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * @author nik
 */
class ExplicitModuleGroupTest : PlatformTestCase() {
  @Test
  fun `test single module`() {
    val module = createModuleInGroup("module", "a", "b")
    val path = ModuleManager.getInstance(myProject).getModuleGroupPath(module)!!
    assertArrayEquals(path, arrayOf("a", "b"))

    val parentGroup = ModuleGroup(listOf("a"))
    assertEmpty(parentGroup.modulesInGroup(myProject, false))
    assertSameElements(parentGroup.modulesInGroup(myProject, true), module)

    val group = assertOneElement(parentGroup.childGroups(grouper))
    assertArrayEquals(group.groupPath, arrayOf("a", "b"))
    assertSameElements(group.modulesInGroup(grouper, false), module)
    assertEmpty(group.childGroups(grouper))
  }

  fun `test two modules`() {
    val module1 = createModuleInGroup("module1", "a")
    val module2 = createModuleInGroup("module2", "a", "b")

    val parentGroup = ModuleGroup(listOf("a"))
    assertSameElements(parentGroup.modulesInGroup(myProject, false), module1)
    assertSameElements(parentGroup.modulesInGroup(myProject, true), module1, module2)

    val group = assertOneElement(parentGroup.childGroups(grouper))
    assertArrayEquals(group.groupPath, arrayOf("a", "b"))
    assertSameElements(group.modulesInGroup(myProject, false), module2)
    assertEmpty(group.childGroups(grouper))
  }

  private val grouper: ModuleGrouper
    get() = ModuleGrouper.instanceFor(myProject)

  private fun createModuleInGroup(name: String, vararg path: String): Module {
    val module = createModule(name)
    val model = ModuleManager.getInstance(myProject).modifiableModel
    model.setModuleGroupPath(module, path)
    runWriteAction { model.commit() }
    return module
  }
}