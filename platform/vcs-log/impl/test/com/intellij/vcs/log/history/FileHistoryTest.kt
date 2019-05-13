// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.data.index.VcsLogPathsIndex
import com.intellij.vcs.log.data.index.VcsLogPathsIndex.ChangeKind.*
import com.intellij.vcs.log.graph.TestGraphBuilder
import com.intellij.vcs.log.graph.TestPermanentGraphInfo
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.asTestGraphString
import com.intellij.vcs.log.graph.graph
import com.intellij.vcs.log.graph.impl.facade.BaseController
import com.intellij.vcs.log.graph.impl.facade.FilteredController
import gnu.trove.THashMap
import gnu.trove.TIntObjectHashMap
import org.junit.Assert
import org.junit.Assume.assumeFalse
import org.junit.Test

class FileHistoryTest {

  fun LinearGraph.assert(startCommit: Int, startPath: FilePath, fileNamesData: FileNamesData, result: TestGraphBuilder.() -> Unit) {
    val permanentGraphInfo = TestPermanentGraphInfo(this)
    val baseController = BaseController(permanentGraphInfo)
    val filteredController = object : FilteredController(baseController, permanentGraphInfo, fileNamesData.getCommits()) {}

    val historyBuilder = FileHistoryBuilder(startCommit, startPath, fileNamesData)
    historyBuilder.accept(filteredController, permanentGraphInfo)

    val expectedResultGraph = graph(result)
    val actualResultGraph = filteredController.collapsedGraph.compiledGraph

    Assert.assertEquals(expectedResultGraph.asTestGraphString(true), actualResultGraph.asTestGraphString(true))
  }

  @Test
  fun linearHistory() {
    val path = LocalFilePath("file.txt", false)
    val fileNamesData = FileNamesDataBuilder(path)
      .addChange(path, 7, listOf(ADDED), listOf(7))
      .addChange(path, 6, listOf(MODIFIED), listOf(7))
      .addChange(path, 4, listOf(MODIFIED), listOf(5))
      .addChange(path, 2, listOf(REMOVED), listOf(3))
      .addChange(path, 0, listOf(ADDED), listOf(1))
      .build()

    graph {
      0(1)
      1(2)
      2(3)
      3(4)
      4(5)
      5(6)
      6(7)
      7()
    }.assert(0, path, fileNamesData) {
      0(2.dot)
      2(4.dot)
      4(6.dot)
      6(7)
      7()
    }
  }

  @Test
  fun historyWithRename() {
    val afterPath = LocalFilePath("after.txt", false)
    val beforePath = LocalFilePath("before.txt", false)
    val fileNamesData = FileNamesDataBuilder(afterPath)
      .addChange(beforePath, 6, listOf(ADDED), listOf(7))
      .addChange(beforePath, 4, listOf(REMOVED), listOf(5))
      .addChange(afterPath, 4, listOf(ADDED), listOf(5))
      .addRename(5, 4, beforePath, afterPath)
      .addChange(afterPath, 2, listOf(MODIFIED), listOf(3))
      .build()

    graph {
      0(1)
      1(2)
      2(3)
      3(4)
      4(5)
      5(6)
      6(7)
      7()
    }.assert(0, afterPath, fileNamesData) {
      2(4.dot)
      4(6.dot)
      6()
    }
  }

  @Test
  fun historyForDeleted() {
    val path = LocalFilePath("file.txt", false)
    val fileNamesData = FileNamesDataBuilder(path)
      .addChange(path, 7, listOf(ADDED), listOf(7))
      .addChange(path, 4, listOf(MODIFIED), listOf(5))
      .addChange(path, 2, listOf(REMOVED), listOf(3))
      .build()

    val graph = graph {
      0(1)
      1(2)
      2(3)
      3(4)
      4(5)
      5(6)
      6(7)
      7()
    }

    graph.assert(0, path, fileNamesData) {
      2(4.dot)
      4(7.dot)
      7()
    }
    graph.assert(4, path, fileNamesData) {
      2(4.dot)
      4(7.dot)
      7()
    }
  }

  @Test
  fun historyWithMerges() {
    val path = LocalFilePath("file.txt", false)
    val fileNamesData = FileNamesDataBuilder(path)
      .addChange(path, 7, listOf(ADDED), listOf(7))
      .addChange(path, 6, listOf(MODIFIED), listOf(7))
      .addChange(path, 4, listOf(NOT_CHANGED, MODIFIED), listOf(6, 5))
      .addChange(path, 3, listOf(MODIFIED), listOf(6))
      .addChange(path, 2, listOf(MODIFIED), listOf(4))
      .addChange(path, 1, listOf(MODIFIED, MODIFIED), listOf(3, 2))
      .addChange(path, 0, listOf(MODIFIED), listOf(1))
      .build()

    graph {
      0(1)
      1(2, 3)
      2(4)
      3(6)
      4(6, 5)
      5(7)
      6(7)
      7()
    }.assert(0, path, fileNamesData) {
      0(1)
      1(3, 2)
      2(6.dot)
      3(6)
      6(7)
      7()
    }
  }

  /**
   * Rename happens in one branch, while the other branch only consists of couple of trivial merge commits.
   */
  @Test
  fun historyWithUndetectedRename() {
    val after = LocalFilePath("after.txt", false)
    val before = LocalFilePath("before.txt", false)
    val fileNamesData = FileNamesDataBuilder(after)
      .addChange(before, 7, listOf(ADDED), listOf(7))
      .addChange(before, 6, listOf(MODIFIED), listOf(7))
      .addChange(before, 5, listOf(MODIFIED), listOf(6))

      .addChange(before, 4, listOf(REMOVED), listOf(5))
      .addChange(after, 4, listOf(ADDED), listOf(5))
      .addRename(5, 4, before, after)

      .addChange(after, 3, listOf(MODIFIED), listOf(4))

      .addChange(before, 2, listOf(MODIFIED, NOT_CHANGED), listOf(6, 5))
      .addChange(before, 1, listOf(REMOVED, NOT_CHANGED), listOf(2, 3))
      .addChange(after, 1, listOf(ADDED, NOT_CHANGED), listOf(2, 3))
      // rename is not detected at merge commit 1
      .addChange(after, 0, listOf(MODIFIED), listOf(1))
      .build()

    graph {
      0(1)
      1(2, 3)
      2(6, 5)
      3(4)
      4(5)
      5(6)
      6(7)
      7()
    }.assert(0, after, fileNamesData) {
      0(3.dot)
      3(4)
      4(5)
      5(6)
      6(7)
      7()
    }
  }

  @Test
  fun historyWithCyclicRenames() {
    val aFile = LocalFilePath("a.txt", false)
    val bFile = LocalFilePath("b.txt", false)
    val fileNamesData = FileNamesDataBuilder(aFile)
      .addChange(aFile, 4, listOf(ADDED), listOf(4))
      .addChange(aFile, 2, listOf(REMOVED), listOf(3))
      .addChange(bFile, 2, listOf(ADDED), listOf(3))
      .addRename(3, 2, aFile, bFile)
      .addChange(bFile, 0, listOf(REMOVED), listOf(1))
      .addChange(aFile, 0, listOf(ADDED), listOf(1))
      .addRename(1, 0, bFile, aFile)
      .build()

    graph {
      0(1)
      1(2)
      2(3)
      3(4)
      4()
    }.assert(0, aFile, fileNamesData) {
      0(2.dot)
      2(4.dot)
      4()
    }
  }

  @Test
  fun historyWithCyclicCaseOnlyRenames() {
    assumeFalse("Case insensitive fs is required", SystemInfo.isFileSystemCaseSensitive)

    val lowercasePath = LocalFilePath("file.txt", false)
    val uppercasePath = LocalFilePath("FILE.TXT", false)
    val mixedPath = LocalFilePath("FiLe.TxT", false)
    val fileNamesData = FileNamesDataBuilder(lowercasePath)
      .addChange(lowercasePath, 7, listOf(ADDED), listOf(7))
      .addChange(lowercasePath, 6, listOf(MODIFIED), listOf(7))

      .addChange(lowercasePath, 5, listOf(REMOVED), listOf(6))
      .addChange(mixedPath, 5, listOf(ADDED), listOf(6))
      .addRename(6, 5, lowercasePath, mixedPath)

      .addChange(mixedPath, 4, listOf(MODIFIED), listOf(5))

      .addChange(mixedPath, 3, listOf(REMOVED), listOf(4))
      .addChange(uppercasePath, 3, listOf(ADDED), listOf(4))
      .addRename(4, 3, mixedPath, uppercasePath)

      .addChange(uppercasePath, 2, listOf(MODIFIED), listOf(3))

      .addChange(uppercasePath, 1, listOf(REMOVED), listOf(2))
      .addChange(lowercasePath, 1, listOf(ADDED), listOf(2))
      .addRename(2, 1, uppercasePath, lowercasePath)

      .build()

    graph {
      0(1)
      1(2)
      2(3)
      3(4)
      4(5)
      5(6)
      6(7)
      7()
    }.assert(0, lowercasePath, fileNamesData) {
      1(2)
      2(3)
      3(4)
      4(5)
      5(6)
      6(7)
      7()
    }
  }

  /*
   * Two file histories: `create initialFile.txt, rename to file.txt, rename to otherFile.txt` and some time later `create file.txt`
   */
  @Test
  fun twoFileByTheSameName() {
    val file = LocalFilePath("file.txt", false)
    val otherFile = LocalFilePath("otherFile.txt", false)
    val initialFile = LocalFilePath("initialFile.txt", false)
    val fileNamesData = FileNamesDataBuilder(file)
      .addChange(file, 0, listOf(MODIFIED), listOf(1))
      .addChange(otherFile, 1, listOf(MODIFIED), listOf(2))
      .addChange(file, 2, listOf(ADDED), listOf(3))
      .addChange(otherFile, 3, listOf(ADDED), listOf(4))
      .addChange(file, 3, listOf(REMOVED), listOf(4))
      .addRename(4, 3, file, otherFile)
      .addChange(file, 5, listOf(ADDED), listOf(6))
      .addChange(initialFile, 5, listOf(REMOVED), listOf(6))
      .addRename(6, 5, initialFile, file)
      .addChange(initialFile, 6, listOf(ADDED), listOf(6))
      .build()

    graph {
      0(1)
      1(2)
      2(3)
      3(4)
      4(5)
      5(6)
      6()
    }.assert(0, file, fileNamesData) {
      0(2.dot)
      2()
    }
  }

  @Test
  fun revertedDeletion() {
    val file = LocalFilePath("file.txt", false)
    val renamedFile = LocalFilePath("renamedFile.txt", false)
    val fileNamesData = FileNamesDataBuilder(file)
      .addChange(renamedFile, 0, listOf(ADDED), listOf(1))
      .addChange(file, 0, listOf(REMOVED), listOf(1))
      .addRename(1, 0, file, renamedFile)
      .addChange(file, 1, listOf(ADDED), listOf(2))
      .addChange(file, 3, listOf(REMOVED), listOf(4))
      .addChange(file, 4, listOf(MODIFIED), listOf(5))
      .addChange(file, 5, listOf(ADDED), listOf(6))
      .build()

    graph {
      0(1)
      1(2)
      2(3)
      3(4)
      4(5)
      5(6)
      6()
    }.assert(0, renamedFile, fileNamesData) {
      0(1)
      1(3.dot)
      3(4)
      4(5)
      5()
    }
  }


  @Test
  fun modifyRenameConflict() {
    val file = LocalFilePath("file.txt", false)
    val renamedFile = LocalFilePath("renamedFile.txt", false)

    val fileNamesData = FileNamesDataBuilder(file)
      .addChange(renamedFile, 0, listOf(MODIFIED), listOf(1))

      .addChange(renamedFile, 1, listOf(MODIFIED, ADDED), listOf(3, 2))
      .addChange(file, 1, listOf(NOT_CHANGED, REMOVED), listOf(3, 2))
      .addRename(2, 1, file, renamedFile)

      .addChange(file, 2, listOf(MODIFIED), listOf(5))

      .addChange(renamedFile, 4, listOf(ADDED), listOf(5))
      .addChange(file, 4, listOf(REMOVED), listOf(5))
      .addRename(5, 4, file, renamedFile)

      .addChange(file, 5, listOf(MODIFIED), listOf(6))
      .addChange(file, 6, listOf(ADDED), listOf(6))
      .build()

    // in order to trigger the bug, parent commits for node 1 in the filtered graph should be in the different order
    // than in the permanent graph
    // this is achieved by filtering out node 3, since in the filtered graph usual edges go first, and only then dotted edges

    graph {
      0(1)
      1(3, 2)
      2(5)
      3(4)
      4(5)
      5(6)
      6()
    }.assert(0, renamedFile, fileNamesData) {
      0(1)
      1(4.dot, 2.u)
      2(5)
      4(5)
      5(6)
      6()
    }
  }
}

private class FileNamesDataBuilder(private val path: FilePath) {
  private val commitsMap: MutableMap<FilePath, TIntObjectHashMap<TIntObjectHashMap<VcsLogPathsIndex.ChangeKind>>> =
    THashMap(FILE_PATH_HASHING_STRATEGY)
  private val renamesMap: MultiMap<Couple<Int>, Couple<FilePath>> = MultiMap.createSmart()

  fun addRename(parent: Int, child: Int, beforePath: FilePath, afterPath: FilePath): FileNamesDataBuilder {
    renamesMap.putValue(Couple(parent, child), Couple(beforePath, afterPath))
    return this
  }

  fun addChange(path: FilePath, commit: Int, changes: List<VcsLogPathsIndex.ChangeKind>, parents: List<Int>): FileNamesDataBuilder {
    commitsMap.getOrPut(path) { TIntObjectHashMap() }.put(commit, parents.zip(changes).toIntObjectMap())
    return this
  }

  fun build(): FileNamesData {
    return object : FileNamesData(path) {
      override fun findRename(parent: Int, child: Int, accept: (Couple<FilePath>) -> Boolean): Couple<FilePath>? {
        return renamesMap[Couple(parent, child)].find { accept(it) }
      }

      override fun getAffectedCommits(path: FilePath): TIntObjectHashMap<TIntObjectHashMap<VcsLogPathsIndex.ChangeKind>> {
        return commitsMap[path] ?: TIntObjectHashMap()
      }
    }
  }
}

private fun <T> List<Pair<Int, T>>.toIntObjectMap(): TIntObjectHashMap<T> {
  val result = TIntObjectHashMap<T>()
  this.forEach { result.put(it.first, it.second) }
  return result
}