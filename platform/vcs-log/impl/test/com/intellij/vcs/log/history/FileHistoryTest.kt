// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.util.containers.CollectionFactory
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
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.junit.Assert
import org.junit.Assume.assumeFalse
import org.junit.Test

class FileHistoryTest {
  fun LinearGraph.assert(startCommit: Int, startPath: FilePath, fileNamesData: FileHistoryData, result: TestGraphBuilder.() -> Unit) {
    val permanentGraphInfo = TestPermanentGraphInfo(this)
    val baseController = BaseController(permanentGraphInfo)
    val filteredController = object : FilteredController(baseController, permanentGraphInfo, fileNamesData.getCommits()) {}

    val historyBuilder = FileHistoryBuilder(startCommit, startPath, fileNamesData, FileHistory.EMPTY)
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
      .addDetectedRename(5, 4, beforePath, afterPath)
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
  fun historyWithUndetectedTrivialRename() {
    val after = LocalFilePath("after.txt", false)
    val before = LocalFilePath("before.txt", false)
    val fileNamesData = FileNamesDataBuilder(after)
      .addChange(before, 7, listOf(ADDED), listOf(7))
      .addChange(before, 6, listOf(MODIFIED), listOf(7))
      .addChange(before, 5, listOf(MODIFIED), listOf(6))
      .addDetectedRename(5, 4, before, after)
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
      .addDetectedRename(3, 2, aFile, bFile)
      .addDetectedRename(1, 0, bFile, aFile)
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
      .addDetectedRename(6, 5, lowercasePath, mixedPath)
      .addChange(mixedPath, 4, listOf(MODIFIED), listOf(5))
      .addDetectedRename(4, 3, mixedPath, uppercasePath)
      .addChange(uppercasePath, 2, listOf(MODIFIED), listOf(3))
      .addDetectedRename(2, 1, uppercasePath, lowercasePath)

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
      .addDetectedRename(4, 3, file, otherFile)
      .addDetectedRename(6, 5, initialFile, file)
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
      .addDetectedRename(1, 0, file, renamedFile)
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
      .addDetectedRename(5, 4, file, renamedFile)
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

  @Test
  fun renamedDirectoryHack() {
    val directory = LocalFilePath("community/platform", true)
    val directoryBeforeRename = LocalFilePath("platform", true)

    val fileNamesData = FileNamesDataBuilder(directory)
      .addChange(directory, 0, listOf(MODIFIED), listOf(1))
      .addChange(directoryBeforeRename, 1, listOf(MODIFIED), listOf(2)) // unrelated modification
      .addChange(directory, 2, listOf(ADDED, ADDED), listOf(3, 4))
      .addChange(directoryBeforeRename, 2, listOf(REMOVED, REMOVED), listOf(3, 4))
      .addRename(3, 2, directoryBeforeRename, directory)
      .addChange(directoryBeforeRename, 5, listOf(MODIFIED), listOf(7))
      .addChange(directoryBeforeRename, 6, listOf(MODIFIED), listOf(8)) // unrelated modification
      .build()

    graph {
      0(1)
      1(2)
      2(3, 4)
      3(5)
      4(6)
      5(7)
      6(8)
      7()
      8()
    }.assert(0, directory, fileNamesData) {
      0(2.dot)
      2(5.dot)
      5()
    }
  }

  /**
   * File is renamed and modified in one branch, modified in another branch. At merge commit rename is not detected by git,
   * and since there are changes in both branches, merge commit is not considered trivial and is not simplified.
   * This means that when computing file history, it is important to walk commits parents in a specific order to correctly track file through a rename.
   */
  @Test
  fun historyWithUndetectedNonTrivialRename() {
    val after = LocalFilePath("after.txt", false)
    val before = LocalFilePath("before.txt", false)
    val fileNamesData = FileNamesDataBuilder(after)
      .addChange(before, 7, listOf(ADDED), listOf(7))
      .addDetectedRename(6, 4, before, after)
      .addChange(after, 3, listOf(MODIFIED), listOf(4))
      .addChange(before, 2, listOf(MODIFIED), listOf(5))
      .addChange(before, 1, listOf(REMOVED, MODIFIED), listOf(2, 3))
      .addChange(after, 1, listOf(ADDED, MODIFIED), listOf(2, 3))
      // rename is not detected at merge commit 1
      .addChange(after, 0, listOf(MODIFIED), listOf(1))
      .build()

    graph {
      0(1)
      1(2, 3)
      2(5)
      3(4)
      4(6)
      5(6)
      6(7)
      7()
    }.assert(0, after, fileNamesData) {
      0(1)
      1(2, 3)
      2(7.dot)
      3(4)
      4(7.dot)
      7()
    }
  }

  @Test
  fun historyWithDeletedAndAddedUnderDifferentName() {
    val after = LocalFilePath("after.txt", false)
    val before = LocalFilePath("before.txt", false)
    val fileNamesData = FileNamesDataBuilder(after)
      .addChange(after, 0, listOf(MODIFIED), listOf(1))
      .addChange(after, 1, listOf(MODIFIED, MODIFIED), listOf(2, 3))
      .addChange(after, 2, listOf(MODIFIED), listOf(6))
      .addChange(after, 3, listOf(MODIFIED), listOf(4))
      .addChange(after, 4, listOf(MODIFIED, ADDED), listOf(6, 5))
      .addChange(before, 5, listOf(REMOVED), listOf(7))
      .addDetectedRename(8, 6, before, after)
      .addChange(before, 7, listOf(MODIFIED), listOf(8))
      .addChange(before, 8, listOf(MODIFIED), listOf(9))
      .addChange(before, 9, listOf(ADDED), listOf(10))
      .build()

    graph {
      0(1)
      1(2, 3)
      2(6)
      3(4)
      4(6, 5)
      5(7)
      6(8)
      7(8)
      8(9)
      9(10)
      10()
    }.assert(0, after, fileNamesData) {
      0(1)
      1(2, 3)
      2(6)
      3(4)
      4(6, 5)
      5(7)
      6(8)
      7(8)
      8(9)
      9()
    }
  }
}

private class FileNamesDataBuilder(private val path: FilePath) {
  private val commitsMap: MutableMap<FilePath, Int2ObjectMap<Int2ObjectMap<VcsLogPathsIndex.ChangeKind>>> =
    CollectionFactory.createCustomHashingStrategyMap(FILE_PATH_HASHING_STRATEGY)
  private val renamesMap: MultiMap<EdgeData<Int>, EdgeData<FilePath>> = MultiMap()

  fun addRename(parent: Int, child: Int, beforePath: FilePath, afterPath: FilePath): FileNamesDataBuilder {
    renamesMap.putValue(EdgeData(parent, child), EdgeData(beforePath, afterPath))
    return this
  }

  fun addChange(path: FilePath, commit: Int, changes: List<VcsLogPathsIndex.ChangeKind>, parents: List<Int>): FileNamesDataBuilder {
    commitsMap.getOrPut(path) { Int2ObjectOpenHashMap() }.put(commit, parents.zip(changes).toIntObjectMap())
    return this
  }

  fun build(): FileHistoryData {
    return object : FileHistoryData(path) {
      override fun findRename(parent: Int, child: Int, path: FilePath, isChildPath: Boolean): EdgeData<FilePath>? {
        return renamesMap[EdgeData(parent, child)].find {
          FILE_PATH_HASHING_STRATEGY.equals(if (isChildPath) it.child else it.parent, path)
        }
      }

      override fun getAffectedCommits(path: FilePath): Int2ObjectMap<Int2ObjectMap<VcsLogPathsIndex.ChangeKind>> {
        return commitsMap[path] ?: Int2ObjectOpenHashMap()
      }
    }.build()
  }
}

private fun FileNamesDataBuilder.addDetectedRename(parent: Int, child: Int, beforePath: FilePath, afterPath: FilePath): FileNamesDataBuilder {
  return addChange(beforePath, child, listOf(REMOVED), listOf(parent))
    .addChange(afterPath, child, listOf(ADDED), listOf(parent))
    .addRename(parent, child, beforePath, afterPath)
}

private fun <T> List<Pair<Int, T>>.toIntObjectMap(): Int2ObjectMap<T> {
  val result = Int2ObjectOpenHashMap<T>()
  this.forEach { result.put(it.first, it.second) }
  return result
}