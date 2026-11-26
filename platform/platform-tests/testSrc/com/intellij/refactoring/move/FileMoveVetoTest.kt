// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.IncorrectOperationException

class FileMoveVetoTest : BasePlatformTestCase() {
  private fun directoryOf(path: String): PsiDirectory {
    myFixture.addFileToProject("$path/file.txt", "")
    return runReadActionBlocking { psiManager.findDirectory(myFixture.tempDirFixture.getFile(path)!!)!! }
  }

  /** Forbids a move of the directory `locked` into the directory `closed`. */
  private fun forbidLockedIntoClosed() {
    val veto = object : FileMoveVeto {
      override fun getProblem(item: PsiFileSystemItem, targetDirectory: PsiDirectory): String? =
        if (item.name == "locked" && targetDirectory.name == "closed") "locked stays out of closed" else null
    }
    ExtensionTestUtil.maskExtensions(FileMoveVeto.EP_NAME, listOf(veto), testRootDisposable)
  }

  private fun canMove(item: PsiElement, target: PsiDirectory): Boolean =
    runReadActionBlocking { MoveHandler.canMove(arrayOf(item), target) }

  private fun checkMove(item: PsiElement, target: PsiDirectory) {
    runReadActionBlocking { MoveFilesOrDirectoriesUtil.checkMove(item, target) }
  }

  fun `test a move that no extension forbids passes the check and is offered`() {
    val locked = directoryOf("locked")
    val closed = directoryOf("closed")

    checkMove(locked, closed)
    assertTrue(canMove(locked, closed))
  }

  fun `test a forbidden move fails the check with the text of the extension`() {
    val locked = directoryOf("locked")
    val closed = directoryOf("closed")
    forbidLockedIntoClosed()

    assertThrows(IncorrectOperationException::class.java, "locked stays out of closed") { checkMove(locked, closed) }
  }

  fun `test a forbidden move is not offered for its target, and the item moves to another target`() {
    val locked = directoryOf("locked")
    val closed = directoryOf("closed")
    val open = directoryOf("open")
    forbidLockedIntoClosed()

    assertFalse(canMove(locked, closed))
    assertTrue(canMove(locked, open))
    assertTrue(runReadActionBlocking { MoveHandler.canMove(arrayOf<PsiElement>(locked), null) })
    checkMove(locked, open)
  }

  fun `test another item moves into the target of a forbidden move`() {
    directoryOf("locked")
    val closed = directoryOf("closed")
    val other = directoryOf("other")
    forbidLockedIntoClosed()

    assertTrue(canMove(other, closed))
    checkMove(other, closed)
  }
}
