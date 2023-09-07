// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.util.paths

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.HierarchicalFilePathComparator.NATURAL
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ReflectionUtil
import com.intellij.vcsUtil.VcsUtil
import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.IntSet
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@TestApplication
class RootDirtySetTest {
  @Test
  fun testEmpty() {
    val dirty = RootDirtySet("/root".filePath, true)
    assertFalse(dirty.belongsTo("".filePath))
    assertFalse(dirty.belongsTo("/root".filePath))
    assertFalse(dirty.belongsTo("/root/test/file.txt".filePath))
    assertCollectFilePathsEmpty(dirty)
    assertInternalSizeFits(dirty, 0, 0)
  }

  @Test
  fun testDirtyRoot1() {
    val dirty = RootDirtySet("/root".filePath, true)
    dirty.markDirty("/root".filePath)

    assertFalse(dirty.belongsTo("".filePath))
    assertTrue(dirty.belongsTo("/root".filePath))
    assertTrue(dirty.belongsTo("/root/test/file.txt".filePath))
    assertCollectFilePathsIs(dirty, "/root")
    assertInternalSizeFits(dirty, 1, 2)
  }

  @Test
  fun testDirtyRoot2() {
    val dirty = RootDirtySet("/root".filePath, true)
    dirty.markDirty("".filePath)

    assertFalse(dirty.belongsTo("".filePath))
    assertTrue(dirty.belongsTo("/root".filePath))
    assertTrue(dirty.belongsTo("/root/test/file.txt".filePath))
    assertCollectFilePathsIs(dirty, "/root")
    assertInternalSizeFits(dirty, 1, 2)
  }

  @Test
  fun testDirtyRoot3() {
    val dirty = RootDirtySet("/root".filePath, true)
    dirty.markDirty("/root/file1.txt".filePath)
    dirty.markDirty("/root/file2.txt".filePath)
    dirty.markDirty("".filePath)

    assertFalse(dirty.belongsTo("".filePath))
    assertTrue(dirty.belongsTo("/root".filePath))
    assertTrue(dirty.belongsTo("/root/test/file.txt".filePath))
    assertCollectFilePathsIs(dirty, "/root")
    assertInternalSizeFits(dirty, 3, 5)
  }

  @Test
  fun testDirtyFiles1() {
    val dirty = RootDirtySet("/root".filePath, true)
    dirty.markDirty("/root/dir/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir/sub/file2.txt".filePath)
    dirty.markDirty("/root/dir2/sub/file1.txt".filePath)

    assertFalse(dirty.belongsTo("".filePath))
    assertFalse(dirty.belongsTo("/root".filePath))
    assertTrue(dirty.belongsTo("/root/dir/sub/file1.txt".filePath))
    assertTrue(dirty.belongsTo("/root/dir/sub/file2.txt".filePath))
    assertTrue(dirty.belongsTo("/root/dir2/sub/file1.txt".filePath))
    assertFalse(dirty.belongsTo("/root/dir2/sub/file2.txt".filePath))
    assertCollectFilePathsIs(dirty,
                             "/root/dir/sub/file1.txt",
                             "/root/dir/sub/file2.txt",
                             "/root/dir2/sub/file1.txt")
    assertInternalSizeFits(dirty, 3, 15)
  }

  @Test
  fun testEverythingDirty1() {
    val dirty = RootDirtySet("/root".filePath, true)
    dirty.markDirty("/root/dir/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir/sub/file2.txt".filePath)
    dirty.markDirty("/root/dir2/sub/file1.txt".filePath)
    dirty.markDirty("/root".filePath)

    assertFalse(dirty.belongsTo("".filePath))
    assertTrue(dirty.belongsTo("/root".filePath))
    assertTrue(dirty.belongsTo("/root/dir/sub/file1.txt".filePath))
    assertTrue(dirty.belongsTo("/root/dir/sub/file2.txt".filePath))
    assertTrue(dirty.belongsTo("/root/dir2/sub/file1.txt".filePath))
    assertCollectFilePathsIs(dirty, "/root")
    assertInternalSizeFits(dirty, 1, 1)
  }

  @Test
  fun testEverythingDirty2() {
    val dirty = RootDirtySet("/root".filePath, true)
    dirty.markDirty("/root/dir/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir/sub/file2.txt".filePath)
    dirty.markDirty("/root/dir2/sub/file1.txt".filePath)
    dirty.markDirty("".filePath)

    assertFalse(dirty.belongsTo("".filePath))
    assertTrue(dirty.belongsTo("/root".filePath))
    assertTrue(dirty.belongsTo("/root/dir/sub/file1.txt".filePath))
    assertTrue(dirty.belongsTo("/root/dir/sub/file2.txt".filePath))
    assertTrue(dirty.belongsTo("/root/dir2/sub/file1.txt".filePath))
    assertCollectFilePathsIs(dirty, "/root")
    assertInternalSizeFits(dirty, 1, 1)
  }

  @Test
  fun testDirectoryMerging1() {
    val dirty = RootDirtySet("/root".filePath, true)
    dirty.markDirty("/root/dir/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir/sub/file2.txt".filePath)

    for (i in 0..1000) {
      dirty.markDirty("/root/dir2/sub/sub2/file1.txt_$i".filePath)
    }

    assertFalse(dirty.belongsTo("".filePath))
    assertFalse(dirty.belongsTo("/root".filePath))
    assertTrue(dirty.belongsTo("/root/dir/sub/file1.txt".filePath))
    assertTrue(dirty.belongsTo("/root/dir/sub/file2.txt".filePath))
    assertTrue(dirty.belongsTo("/root/dir2/sub/sub2/file1.txt_15".filePath))
    assertTrue(dirty.belongsTo("/root/dir2/sub/sub2/file1.txt_everything".filePath))
    assertFalse(dirty.belongsTo("/root/dir2/sub/file1.txt_15".filePath))
    assertCollectFilePathsIs(dirty,
                             "/root/dir/sub/file1.txt",
                             "/root/dir/sub/file2.txt",
                             "/root/dir2/sub/sub2")

    assertInternalSizeFits(dirty, 35, 70)
  }

  @Test
  fun testDirectoryMerging2() {
    val dirty = RootDirtySet("/root".filePath, true)
    dirty.markDirty("/root/dir/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir/sub/file2.txt".filePath)

    for (i in 0..1000) {
      dirty.markDirty("/root/dir2/sub/sub2/file1.txt_$i".filePath)
    }
    for (i in 0..1000) {
      dirty.markDirty("/root/dir2/sub/sub3/file1.txt_$i".filePath)
    }

    assertFalse(dirty.belongsTo("".filePath))
    assertFalse(dirty.belongsTo("/root".filePath))
    assertTrue(dirty.belongsTo("/root/dir/sub/file1.txt".filePath))
    assertTrue(dirty.belongsTo("/root/dir/sub/file2.txt".filePath))
    assertTrue(dirty.belongsTo("/root/dir2/sub/sub2/file1.txt_15".filePath))
    assertTrue(dirty.belongsTo("/root/dir2/sub/sub2/file1.txt_everything".filePath))
    assertTrue(dirty.belongsTo("/root/dir2/sub/file1.txt_15".filePath))
    assertCollectFilePathsIs(dirty,
                             "/root/dir/sub/file1.txt",
                             "/root/dir/sub/file2.txt",
                             "/root/dir2/sub")
    assertInternalSizeFits(dirty, 60, 130)
  }

  @Test
  fun testDirectoryMerging3() {
    val dirty = RootDirtySet("/root".filePath, true)
    dirty.markDirty("/root/dir/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir/sub/file2.txt".filePath)

    for (i in 0..1000) {
      dirty.markDirty("/root/dir2/sub/sub$i/file1.txt".filePath)
    }

    assertFalse(dirty.belongsTo("".filePath))
    assertFalse(dirty.belongsTo("/root".filePath))
    assertTrue(dirty.belongsTo("/root/dir/sub/file1.txt".filePath))
    assertTrue(dirty.belongsTo("/root/dir/sub/file2.txt".filePath))
    assertTrue(dirty.belongsTo("/root/dir2/sub/sub2/file1.txt_15".filePath))
    assertTrue(dirty.belongsTo("/root/dir2/sub/sub2/file1.txt_everything".filePath))
    assertFalse(dirty.belongsTo("/root/dir2/file1.txt_15".filePath))
    assertCollectFilePathsIs(dirty,
                             "/root/dir/sub/file1.txt",
                             "/root/dir/sub/file2.txt",
                             "/root/dir2/sub")

    assertInternalSizeFits(dirty, 35, 100)
  }

  @Test
  fun testDirectoryMergingNoSiblingErasure() {
    val dirty = RootDirtySet("/root".filePath, true)
    dirty.markDirty("/root/dir/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir/sub/file2.txt".filePath)

    for (i in 0..1000) {
      dirty.markDirty("/root/dir2/sub/sub$i/file1.txt".filePath)
    }
    dirty.markDirty("/root/dir3/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir3/sub/file2.txt".filePath)
    for (i in 0..1000) {
      dirty.markDirty("/root/dir4/sub/sub$i/file1.txt".filePath)
    }
    dirty.markDirty("/root/dir3/sub/file3.txt".filePath)
    dirty.markDirty("/root/dir3/sub/file4.txt".filePath)
    for (i in 0..1000) {
      dirty.markDirty("/root/dir5/sub/sub$i/file1.txt".filePath)
    }

    assertCollectFilePathsIs(dirty,
                             "/root/dir/sub/file1.txt/",
                             "/root/dir/sub/file2.txt/",
                             "/root/dir2/sub/",
                             "/root/dir3/sub/file1.txt",
                             "/root/dir3/sub/file2.txt",
                             "/root/dir3/sub/file3.txt",
                             "/root/dir3/sub/file4.txt",
                             "/root/dir4/sub",
                             "/root/dir5/sub")
    assertInternalSizeFits(dirty, 90, 270)
  }

  @Test
  fun testDirectoryMergingExplosiveSize1() {
    val dirty = RootDirtySet("/root".filePath, true)
    dirty.markDirty("/root/dir/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir/sub/file2.txt".filePath)

    for (i in 0..1000) {
      dirty.markDirty("/root/dir2/sub/sub$i/file1.txt".filePath)
    }
    dirty.markDirty("/root/dir3/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir3/sub/file2.txt".filePath)
    for (i in 0..1000) {
      dirty.markDirty("/root/dir4/sub/sub$i/file1.txt".filePath)
    }
    dirty.markDirty("/root/dir3/sub/file3.txt".filePath)
    dirty.markDirty("/root/dir3/sub/file4.txt".filePath)

    for (k in 10..1000) {
      for (i in 0..1000) {
        dirty.markDirty("/root/dir$k/sub/sub$i/file1.txt".filePath)
      }
    }

    assertCollectFilePathsIs(dirty, "/root")
    assertInternalSizeFits(dirty, 1, 1)
  }

  @Test
  fun testDirectoryMergingExplosiveSize2() {
    val dirty = RootDirtySet("/root".filePath, true)
    dirty.markDirty("/root/subroot/dir/sub/file1.txt".filePath)
    dirty.markDirty("/root/subroot/dir/sub/file2.txt".filePath)

    for (i in 0..1000) {
      dirty.markDirty("/root/subroot/dir2/sub/sub$i/file1.txt".filePath)
    }
    dirty.markDirty("/root/subroot/dir3/sub/file1.txt".filePath)
    dirty.markDirty("/root/subroot/dir3/sub/file2.txt".filePath)
    for (i in 0..1000) {
      dirty.markDirty("/root/subroot/dir4/sub/sub$i/file1.txt".filePath)
    }
    dirty.markDirty("/root/subroot/dir3/sub/file3.txt".filePath)
    dirty.markDirty("/root/subroot/dir3/sub/file4.txt".filePath)

    for (k in 10..1000) {
      for (i in 0..1000) {
        dirty.markDirty("/root/subroot/dir$k/sub/sub$i/file1.txt".filePath)
      }
    }

    assertCollectFilePathsIs(dirty, "/root/subroot")
    assertInternalSizeFits(dirty, 340, 1200)
  }

  @Test
  fun testDirectoryMergingLoops1() {
    val dirty = RootDirtySet("/root".filePath, true)
    dirty.markDirty("/root/dir/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir/sub/file2.txt".filePath)

    for (i in 0..1000) {
      for (j in 0..1000) {
        dirty.markDirty("/root/dir2/sub$i/sub/file$j.txt".filePath)
      }
    }

    assertCollectFilePathsIs(dirty,
                             "/root/dir/sub/file1.txt/",
                             "/root/dir/sub/file2.txt/",
                             "/root/dir2")
    assertInternalSizeFits(dirty, 60, 190)
  }

  @Test
  fun testDirectoryMergingLoops2() {
    val dirty = RootDirtySet("/root".filePath, true)
    dirty.markDirty("/root/dir/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir/sub/file2.txt".filePath)

    for (i in 0..1000) {
      for (j in 0..1000) {
        dirty.markDirty("/root/dir2/sub$j/sub/file$i.txt".filePath)
      }
    }

    assertCollectFilePathsIs(dirty,
                             "/root/dir/sub/file1.txt/",
                             "/root/dir/sub/file2.txt/",
                             "/root/dir2")
    assertInternalSizeFits(dirty, 60, 190)
  }

  @Test
  fun testDirectoryMergingLoops3() {
    val dirty = RootDirtySet("/root".filePath, true)
    dirty.markDirty("/root/dir/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir/sub/file2.txt".filePath)

    for (i in 0..1000) {
      for (j in 0..20) {
        dirty.markDirty("/root/dir2/sub$j/sub/file$i.txt".filePath)
      }
    }

    assertCollectFilePathsIs(dirty,
                             "/root/dir/sub/file1.txt/",
                             "/root/dir/sub/file2.txt/",
                             "/root/dir2")
    assertInternalSizeFits(dirty, 60, 190)
  }

  @Test
  fun testDirectoryMergingLoops4() {
    val dirty = RootDirtySet("/root".filePath, true)
    dirty.markDirty("/root/dir/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir/sub/file2.txt".filePath)

    for (i in 0..1000) {
      for (j in 0..20) {
        dirty.markDirty("/root/dir2/sub$i/sub/file$j.txt".filePath)
      }
    }

    assertCollectFilePathsIs(dirty,
                             "/root/dir/sub/file1.txt/",
                             "/root/dir/sub/file2.txt/",
                             "/root/dir2")
    assertInternalSizeFits(dirty, 60, 80)
  }

  @Test
  fun testDirectoryMergingLoops5() {
    val dirty = RootDirtySet("/root".filePath, true)
    dirty.markDirty("/root/dir/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir/sub/file2.txt".filePath)

    for (i in 0..20) {
      for (j in 0..1000) {
        dirty.markDirty("/root/dir2/sub$j/sub/file$i.txt".filePath)
      }
    }

    assertCollectFilePathsIs(dirty,
                             "/root/dir/sub/file1.txt/",
                             "/root/dir/sub/file2.txt/",
                             "/root/dir2")
    assertInternalSizeFits(dirty, 60, 190)
  }

  @Test
  fun testDirectoryMergingLoops6() {
    val dirty = RootDirtySet("/root".filePath, true)
    dirty.markDirty("/root/dir/sub/file1.txt".filePath)
    dirty.markDirty("/root/dir/sub/file2.txt".filePath)

    for (i in 0..20) {
      for (j in 0..1000) {
        dirty.markDirty("/root/dir2/sub$i/sub/file$j.txt".filePath)
      }
    }

    assertCollectFilePathsIs(dirty,
                             "/root/dir/sub/file1.txt/",
                             "/root/dir/sub/file2.txt/",
                             "/root/dir2")
    assertInternalSizeFits(dirty, 60, 190)
  }

  private fun assertCollectFilePathsEmpty(dirty: RootDirtySet) {
    assertEquals(emptySet<FilePath>(), dirty.collectFilePaths().toSortedSet(NATURAL))
  }

  private fun assertCollectFilePathsIs(dirty: RootDirtySet, expected: String, vararg moreExpected: String) {
    assertEquals((moreExpected.asList() + expected).map { it.filePath }.toSortedSet(NATURAL),
                 dirty.collectFilePaths().toSortedSet(NATURAL))
  }

  private fun assertInternalSizeFits(dirty: RootDirtySet, strings: Int, hashes: Int) {
    val paths = ReflectionUtil.getField(RootDirtySet::class.java, dirty, Set::class.java, "myPaths")
    val pathHashes = ReflectionUtil.getField(RootDirtySet::class.java, dirty, IntSet::class.java, "myPathHashSet")
    val hashCounters = ReflectionUtil.getField(RootDirtySet::class.java, dirty, Int2IntMap::class.java, "myPathHashCounters")
    val actualStrings = paths.size
    val actualHashes = pathHashes.size + hashCounters.size
    assertTrue(actualStrings <= strings && actualStrings * 2 + 10 > strings,
               "${paths.size}: ${paths}")
    assertTrue(actualHashes <= hashes && actualHashes * 2 + 10 > hashes,
               "${pathHashes.size} - ${hashCounters.size}")
  }
}

private val String.filePath: FilePath get() = VcsUtil.getFilePath(this, true)

