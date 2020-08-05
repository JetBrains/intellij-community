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
import com.intellij.testFramework.HeavyPlatformTestCase
import org.junit.Assert
import org.junit.Test

class GroupModulesByQualifiedNamesTest : HeavyPlatformTestCase() {
  @Test
  fun `test single module`() {
    val module = createModule("a.b.module")
    assertEquals("module", grouper.getShortenedName(module))

    val parentGroup = ModuleGroup(listOf("a"))
    assertEmpty(parentGroup.modulesInGroup(grouper, false))
    assertEmpty(parentGroup.modulesInGroup(myProject, false))
    assertSameElements(parentGroup.modulesInGroup(grouper, true), module)

    val group = assertOneElement(parentGroup.childGroups(grouper))
    Assert.assertArrayEquals(group.groupPath, arrayOf("a", "b"))
    Assert.assertArrayEquals(assertOneElement(parentGroup.childGroups(myProject)).groupPath, arrayOf("a", "b"))
    assertSameElements(group.modulesInGroup(grouper, false), module)
    assertSameElements(group.modulesInGroup(myProject), module)
    assertEmpty(group.childGroups(grouper))
    assertEmpty(group.childGroups(myProject))
  }

  fun `test two modules`() {
    val module1 = createModule("a.module1")
    val module2 = createModule("a.b.module2")

    assertEquals("module1", grouper.getShortenedName(module1))
    assertEquals("module2", grouper.getShortenedName(module2))

    val parentGroup = ModuleGroup(listOf("a"))
    assertSameElements(parentGroup.modulesInGroup(grouper, false), module1)
    assertSameElements(parentGroup.modulesInGroup(myProject), module1)
    assertSameElements(parentGroup.modulesInGroup(grouper, true), module1, module2)

    val group = assertOneElement(parentGroup.childGroups(grouper))
    Assert.assertArrayEquals(group.groupPath, arrayOf("a", "b"))
    Assert.assertArrayEquals(assertOneElement(parentGroup.childGroups(myProject)).groupPath, arrayOf("a", "b"))
    assertSameElements(group.modulesInGroup(grouper, false), module2)
    assertSameElements(group.modulesInGroup(myProject), module2)
    assertEmpty(group.childGroups(grouper))
    assertEmpty(group.childGroups(myProject))
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
    assertSameElements(parentGroup.modulesInGroup(myProject), module1, module2, module3)
    assertSameElements(parentGroup.modulesInGroup(grouper, true), module1, module2, module3)

    assertEmpty(parentGroup.childGroups(grouper))
    assertEmpty(parentGroup.childGroups(myProject))
  }

  fun `test module as a group with deep ancestor`() {
    val module1 = createModule("a.foo")
    val module2 = createModule("a.foo.bar.baz")

    assertEquals("foo", grouper.getShortenedName(module1))
    assertEquals("baz", grouper.getShortenedName(module2))

    val parentGroup = ModuleGroup(listOf("a"))
    assertSameElements(parentGroup.modulesInGroup(grouper, false), module1, module2)
    assertSameElements(parentGroup.modulesInGroup(myProject), module1, module2)
    assertSameElements(parentGroup.modulesInGroup(grouper, true), module1, module2)

    assertEmpty(parentGroup.childGroups(grouper))
    assertEmpty(parentGroup.childGroups(myProject))
  }

  fun `test grand parent group`() {
    val module1 = createModule("a.foo.x.m1")
    val module2 = createModule("a.foo.x.m2")
    val module3 = createModule("a.bar.x.m3")
    val module4 = createModule("a.bar.x.m4")

    val parentGroup = ModuleGroup(listOf("a"))
    assertEmpty(parentGroup.modulesInGroup(grouper, false))
    assertEmpty(parentGroup.modulesInGroup(myProject))
    assertSameElements(parentGroup.modulesInGroup(grouper, true), module1, module2, module3, module4)

    val fooGroup = ModuleGroup(listOf("a", "foo"))
    val barGroup = ModuleGroup(listOf("a", "bar"))
    assertSameElements(parentGroup.childGroups(grouper), fooGroup, barGroup)
    assertSameElements(parentGroup.childGroups(myProject), fooGroup, barGroup)
    val fooXGroup = ModuleGroup(listOf("a", "foo", "x"))
    val barXGroup = ModuleGroup(listOf("a", "bar", "x"))
    assertSameElements(fooGroup.childGroups(grouper), fooXGroup)
    assertSameElements(fooGroup.childGroups(myProject), fooXGroup)
    assertSameElements(barGroup.childGroups(grouper), barXGroup)
    assertSameElements(barGroup.childGroups(myProject), barXGroup)
  }

  fun `test names with incorrect chars after dots`() {
    val module1 = createModule("a.foo-1.2")
    val module2 = createModule("a.foo-1.3")

    assertEquals("foo-1.2", grouper.getShortenedName(module1))
    assertEquals("foo-1.3", grouper.getShortenedName(module2))

    val parentGroup = ModuleGroup(listOf("a"))
    assertSameElements(parentGroup.modulesInGroup(grouper, false), module1, module2)
    assertSameElements(parentGroup.modulesInGroup(myProject), module1, module2)
    assertSameElements(parentGroup.modulesInGroup(grouper, true), module1, module2)

    assertEmpty(parentGroup.childGroups(grouper))
    assertEmpty(parentGroup.childGroups(myProject))
  }

  private val grouper: ModuleGrouper
    get() = getQualifiedNameModuleGrouper(myProject)
}
