// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data.index

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.util.containers.BiDirectionalEnumerator
import com.intellij.util.containers.ContainerUtil.canonicalStrategy
import com.intellij.vcs.log.data.index.VcsLogPathsIndex.ChangeData
import com.intellij.vcs.log.data.index.VcsLogPathsIndex.ChangeKind.*
import junit.framework.TestCase

class FileNamesDataTest : TestCase() {
  fun `test linear file history`() {
    val data = TestFileNamesData()

    val file = LocalFilePath("file.txt", false)

    data.add(0, file, mutableListOf(modification()), listOf())
    data.add(1, file, mutableListOf(modification()), listOf(0))
    data.add(2, file, mutableListOf(modification()), listOf(1))

    assertEquals(mapOf(Pair(0, file), Pair(1, file), Pair(2, file)), data.buildPathsMap())
  }

  fun `test history with rename`() {
    val data = TestFileNamesData()

    val file = LocalFilePath("file.txt", false)
    val oldFile = LocalFilePath("oldfile.txt", false)

    val renameFrom = ChangeData(RENAMED_FROM, data.getPathId(file))
    val renameTo = ChangeData(RENAMED_TO, data.getPathId(oldFile))

    data.add(0, oldFile, mutableListOf(modification()), listOf())
    data.add(1, file, mutableListOf(renameTo), listOf(0))
    data.add(1, oldFile, mutableListOf(renameFrom), listOf(0))
    data.add(2, file, mutableListOf(modification()), listOf(1))

    assertEquals(file, data.getPathInChildRevision(1, 0, oldFile))
    assertEquals(oldFile, data.getPathInParentRevision(1, 0, file))
    assertEquals(mapOf(Pair(0, oldFile), Pair(1, file), Pair(2, file)), data.buildPathsMap())
  }

  private fun modification() = ChangeData(MODIFIED, -1)
}

private class TestFileNamesData : IndexDataGetter.FileNamesData() {
  private val pathEnumerator = BiDirectionalEnumerator<String>(1, canonicalStrategy<String>())

  override fun getPathById(pathId: Int): FilePath = LocalFilePath(pathEnumerator.getValue(pathId), false)

  fun getPathId(path: FilePath) = pathEnumerator.enumerate(path.path)
}