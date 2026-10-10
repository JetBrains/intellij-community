// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.createSmartFileRange
import com.intellij.psi.findElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ModPlanConflictsTest : BasePlatformTestCase() {
  private fun addFile(name: String, text: String): PsiFile = myFixture.addFileToProject(name, text)

  private fun ModPlanConflicts.at(file: PsiFile): ModPlanConflict = conflicts.single { it.location?.virtualFile == file.virtualFile }

  fun `test merge joins messages per location`() {
    val file = addFile("a.txt", "text")
    val otherFile = addFile("b.txt", "other text")
    val firstConflicts = ModPlanConflicts.builder().broken(file, "one").fixable(otherFile, "two").build()
    val secondConflicts = ModPlanConflicts.builder().broken(file, "three").fixable(otherFile, "four").broken(file, "one").build()

    val mergedConflicts = firstConflicts.merge(secondConflicts)

    assertEquals(listOf("one", "three"), mergedConflicts.at(file).messages)
    assertEquals(listOf("two", "four"), mergedConflicts.at(otherFile).messages)
    assertEquals(2, mergedConflicts.conflicts.size)
  }

  fun `test builder records the kind`() {
    val file = addFile("a.txt", "text")
    val otherFile = addFile("b.txt", "other text")

    val conflicts = ModPlanConflicts.builder().broken(file, "broken").fixable(otherFile, "fixable").build()

    assertEquals(ModPlanConflictKind.BROKEN, conflicts.at(file).kind)
    assertEquals(ModPlanConflictKind.FIXABLE, conflicts.at(otherFile).kind)
    assertTrue(conflicts.hasBroken())
    assertTrue(conflicts.needsConfirmation())
    assertFalse(conflicts.conflicts.isEmpty())
    assertTrue(ModPlanConflicts.EMPTY.conflicts.isEmpty())
    assertFalse(ModPlanConflicts.EMPTY.needsConfirmation())
    assertFalse(ModPlanConflicts.EMPTY.hasBroken())
    assertFalse(ModPlanConflicts.builder().fixable(file, "fixable").build().hasBroken())
  }

  fun `test fixable and broken of one place merge into broken`() {
    val file = addFile("a.txt", "text")

    val conflicts = ModPlanConflicts.builder().fixable(file, "fixable").broken(file, "broken").build()

    assertEquals(ModPlanConflictKind.BROKEN, conflicts.at(file).kind)
    assertEquals(listOf("fixable", "broken"), conflicts.at(file).messages)
    val reversedConflicts = ModPlanConflicts.builder().broken(file, "broken").fixable(file, "fixable").build()
    assertEquals(ModPlanConflictKind.BROKEN, reversedConflicts.at(file).kind)
  }

  fun `test a non-code usage is the weakest kind`() {
    val file = addFile("a.txt", "text")

    val conflicts = ModPlanConflicts.builder().nonCodeUsage(file, null, "nonCodeUsage").fixable(file, "fixable").build()

    assertEquals(1, conflicts.conflicts.size)
    assertEquals(ModPlanConflictKind.FIXABLE, conflicts.at(file).kind)
    assertEquals(listOf("nonCodeUsage", "fixable"), conflicts.at(file).messages)
    assertTrue(conflicts.nonCodeUsages().isEmpty())
  }

  fun `test non-code usages alone need no confirmation`() {
    val file = addFile("a.txt", "text")

    val conflicts = ModPlanConflicts.builder().nonCodeUsage(file, TextRange(1, 3), "nonCodeUsage").build()

    val nonCodeUsage = conflicts.nonCodeUsages().single()
    assertEquals(ModPlanConflictKind.NON_CODE_USAGE, nonCodeUsage.kind)
    assertEquals(listOf("nonCodeUsage"), nonCodeUsage.messages)
    assertEquals(TextRange(1, 3), TextRange.create(nonCodeUsage.location!!.range!!))
    assertEquals(file.virtualFile, nonCodeUsage.location!!.virtualFile)
    assertFalse(conflicts.conflicts.isEmpty())
    assertFalse(conflicts.needsConfirmation())
    assertFalse(conflicts.hasBroken())
  }

  fun `test non-code usages of different ranges of one element stay apart`() {
    val file = addFile("a.txt", "text")

    val conflicts = ModPlanConflicts.builder().nonCodeUsage(file, TextRange(0, 1), "first").nonCodeUsage(file, TextRange(2, 3), "second").build()

    assertEquals(2, conflicts.nonCodeUsages().size)
  }

  fun `test non-code usages of one range keep both messages`() {
    val file = addFile("a.txt", "text")

    val conflicts = ModPlanConflicts.builder()
      .nonCodeUsage(file, TextRange(1, 3), "first").nonCodeUsage(file, TextRange(1, 3), "second").nonCodeUsage(file, TextRange(1, 3), "first")
      .build()

    assertEquals(listOf("first", "second"), conflicts.nonCodeUsages().single().messages)
  }

  fun `test a conflict without an element has no location and merges with the others of the refactoring`() {
    val conflicts = ModPlanConflicts.builder().broken(null, "one").fixable(null, "two").build()

    val conflict = conflicts.conflicts.single()
    assertNull(conflict.location)
    assertEquals(listOf("one", "two"), conflict.messages)
    assertEquals(ModPlanConflictKind.BROKEN, conflict.kind)
  }

  fun `test no unloaded modules give no conflict`() {
    val file = addFile("a.txt", "text")

    assertTrue(ModPlanConflicts.builder().unloadedModules(listOf(file)).build().conflicts.isEmpty())
    assertTrue(ModPlanConflicts.builder().unloadedModules(emptyList()).build().conflicts.isEmpty())
  }

  fun `test toShowConflicts keeps the messages and drops the non-code usages`() {
    val file = addFile("a.txt", "text")
    val otherFile = addFile("b.txt", "other text")
    val nonCodeUsageFile = addFile("c.txt", "nonCodeUsage text")
    val conflicts = ModPlanConflicts.builder()
      .broken(file, "one").fixable(file, "two").fixable(otherFile, "three").nonCodeUsage(nonCodeUsageFile, null, "nonCodeUsage")
      .build()

    val shownConflicts = runReadActionBlocking { conflicts.toShowConflicts(file) }

    assertEquals(2, shownConflicts.size)
    val shownFiles = shownConflicts.keys.map { it.containingFile }
    assertEquals(listOf(file, otherFile), shownFiles)
    assertEquals(listOf("one", "two"), shownConflicts.values.first().messages)
    assertEquals(listOf("three"), shownConflicts.values.last().messages)
  }

  fun `test toShowConflicts puts a conflict without an element on the anchor`() {
    val anchor = addFile("a.txt", "text")
    val otherFile = addFile("b.txt", "other text")
    val conflicts = ModPlanConflicts.builder().broken(null, "everywhere").fixable(otherFile, "there").build()

    val shownConflicts = runReadActionBlocking { conflicts.toShowConflicts(anchor) }

    assertEquals(listOf("everywhere"), shownConflicts[anchor]!!.messages)
    assertEquals(listOf("there"), shownConflicts.entries.single { it.key != anchor }.value.messages)
  }

  fun `test toShowConflicts puts a conflict of a place that is gone on the anchor`() {
    val anchor = addFile("a.txt", "text")
    val goneFile = addFile("b.txt", "text")
    val goneConflicts = ModPlanConflicts(listOf(
      ModPlanConflict(runReadActionBlocking { goneFile.createSmartFileRange() }, listOf("gone"), ModPlanConflictKind.BROKEN)
    ))
    WriteCommandAction.runWriteCommandAction(project) { goneFile.delete() }

    val shownConflicts = runReadActionBlocking { goneConflicts.toShowConflicts(anchor) }

    assertEquals(setOf(anchor), shownConflicts.keys)
  }

  fun `test findElement of a whole file is the file`() {
    val file = addFile("a.txt", "text")

    val foundElement = runReadActionBlocking { file.createSmartFileRange()!!.findElement() }

    assertEquals(file, foundElement)
  }

  fun `test findElement of a range inside an element is the file`() {
    val file = addFile("a.txt", "text")

    val foundElement = runReadActionBlocking { file.createSmartFileRange(TextRange(1, 3))!!.findElement() }

    assertEquals(file, foundElement)
  }

  fun `test findElement of a deleted file is null`() {
    val file = addFile("a.txt", "text")
    val location = runReadActionBlocking { file.createSmartFileRange()!! }
    WriteCommandAction.runWriteCommandAction(project) { file.delete() }

    assertNull(runReadActionBlocking { location.findElement() })
  }

  fun `test a directory has no location`() {
    val directory = addFile("dir/a.txt", "text").containingDirectory!!

    assertNull(runReadActionBlocking { directory.createSmartFileRange() })
  }
}
