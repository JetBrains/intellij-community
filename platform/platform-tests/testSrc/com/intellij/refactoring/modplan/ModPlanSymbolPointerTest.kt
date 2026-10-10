// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.model.SymbolPointer
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteUsageInfoUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ModPlanSymbolPointerTest : BasePlatformTestCase() {
  fun `test subjects of one element are different objects of the same symbol`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")

    assertFalse(SymbolPointer.of(a) == SymbolPointer.of(a))
    assertTrue(SymbolPointer.of(a).isSameSymbol(SymbolPointer.of(a)))
    assertFalse(SymbolPointer.of(a).isSameSymbol(SymbolPointer.of(b)))
  }

  fun `test a subject knows its element until the element is deleted`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val subject = SymbolPointer.of(a)
    assertEquals(a, subject.element())
    assertTrue(subject.isValid)

    WriteCommandAction.runWriteCommandAction(project) { a.delete() }

    assertFalse(subject.isValid)
    assertNull(subject.element())
  }

  fun `test a request lists the live elements of its subjects`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    val request =
      ModPlanRequest(project, listOf(SymbolPointer.of(a), SymbolPointer.of(b)), ModPlanOptionValues.EMPTY)

    assertEquals(listOf(a, b), ModPlanSafeDeleteUsageInfoUtil.elementsOf(request.subjects()).toList())

    WriteCommandAction.runWriteCommandAction(project) { b.delete() }

    assertEquals(listOf(a), ModPlanSafeDeleteUsageInfoUtil.elementsOf(request.subjects()).toList())
  }
}
