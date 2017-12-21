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
import com.intellij.openapi.module.ModuleGrouper
import com.intellij.testFramework.PlatformTestCase
import org.junit.Assert
import org.junit.Test

/**
 * @author nik
 */
class GroupModulesByQualifiedNamesTest : PlatformTestCase() {
  @Test
  fun `test single module`() {
    val module = createModule("a.b.module")
    assertEquals("module", grouper.getShortenedName(module))

    val parentGroup = ModuleGroup(listOf("a"))
    assertEmpty(parentGroup.modulesInGroup(grouper, false))
    assertSameElements(parentGroup.modulesInGroup(grouper, true), module)

    val group = assertOneElement(parentGroup.childGroups(grouper))
    Assert.assertArrayEquals(group.groupPath, arrayOf("a", "b"))
    assertSameElements(group.modulesInGroup(grouper, false), module)
    assertEmpty(group.childGroups(grouper))
  }

  fun `test two modules`() {
    val module1 = createModule("a.module1")
    val module2 = createModule("a.b.module2")

    assertEquals("module1", grouper.getShortenedName(module1))
    assertEquals("module2", grouper.getShortenedName(module2))

    val parentGroup = ModuleGroup(listOf("a"))
    assertSameElements(parentGroup.modulesInGroup(grouper, false), module1)
    assertSameElements(parentGroup.modulesInGroup(grouper, true), module1, module2)

    val group = assertOneElement(parentGroup.childGroups(grouper))
    Assert.assertArrayEquals(group.groupPath, arrayOf("a", "b"))
    assertSameElements(group.modulesInGroup(grouper, false), module2)
    assertEmpty(group.childGroups(grouper))
  }

  fun `test module as a group`() {
    val module1 = createModule("a.foo")
    val module2 = createModule("a.foo.bar")
    val module3 = createModule("a.foo.bar.baz")

    assertEquals("foo", grouper.getShortenedName(module1))
    assertEquals("bar", grouper.getShortenedName(module2))
    assertEquals("baz", grouper.getShortenedName(module3))

    val parentGroup = ModuleGroup(listOf("a"))
    assertSameElements(parentGroup.modulesInGroup(grouper, false), module1, module2, module3)
    assertSameElements(parentGroup.modulesInGroup(grouper, true), module1, module2, module3)

    assertEmpty(parentGroup.childGroups(grouper))
  }

  fun `test module as a group with deep ancestor`() {
    val module1 = createModule("a.foo")
    val module2 = createModule("a.foo.bar.baz")

    assertEquals("foo", grouper.getShortenedName(module1))
    assertEquals("baz", grouper.getShortenedName(module2))

    val parentGroup = ModuleGroup(listOf("a"))
    assertSameElements(parentGroup.modulesInGroup(grouper, false), module1, module2)
    assertSameElements(parentGroup.modulesInGroup(grouper, true), module1, module2)

    assertEmpty(parentGroup.childGroups(grouper))
  }

  private val grouper: ModuleGrouper
    get() = getQualifiedNameModuleGrouper(myProject)
}
