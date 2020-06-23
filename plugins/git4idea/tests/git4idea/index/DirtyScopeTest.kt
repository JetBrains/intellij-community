// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.util.Comparing
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.vcsUtil.VcsUtil.getFilePath
import junit.framework.TestCase

class DirtyScopeTest : LightPlatformTestCase() {
  private val root get() = project.baseDir

  fun `test recursive dirs`() {
    val scope = DirtyScope.Paths(root)
    scope.addDirtyPaths(listOf(getFilePath(root, "dir1"), getFilePath(root, "dir2/dir3")), true)
    TestCase.assertTrue(scope.belongsTo(root, getFilePath(root)))
    TestCase.assertTrue(scope.belongsTo(root, getFilePath(root, "dir1/file")))
    TestCase.assertTrue(scope.belongsTo(root, getFilePath(root, "dir2/dir3/file")))
    TestCase.assertFalse(scope.belongsTo(root, getFilePath(root, "dir2/file")))
    TestCase.assertFalse(scope.belongsTo(root, getFilePath(root.parent)))
  }

  fun `test pack`() {
    val scope = DirtyScope.Paths(root)
    scope.addDirtyPaths(listOf(getFilePath(root, "dir1/file"), getFilePath(root, "dir1/dir2/file"), getFilePath(root, "dir2/file")), false)
    scope.addDirtyPaths(listOf(getFilePath(root, "dir1/dir2"),
                               getFilePath(root, "dir1/dir3"),
                               getFilePath(root, "dir1")), true)
    scope.pack()
    TestCase.assertTrue(Comparing.haveEqualElements(scope.dirtyPaths(),
                                                    listOf(getFilePath(root, "dir1"), getFilePath(root, "dir2/file"))))
  }

  fun `test addTo`() {
    val targetScope = DirtyScope.Paths(root)
    val sourceScope = DirtyScope.Paths(root)

    sourceScope.addDirtyPaths(listOf(getFilePath(root, "dir1"), getFilePath(root, "dir2")), true)
    sourceScope.addDirtyPaths(listOf(getFilePath(root, "dir3/file")), false)

    targetScope.addDirtyPaths(listOf(getFilePath(root, "dir2"), getFilePath(root, "dir4")), true)
    targetScope.addDirtyPaths(listOf(getFilePath(root, "dir1/file"), getFilePath(root, "dir1/dir2/file")), false)

    sourceScope.addTo(targetScope)
    TestCase.assertTrue(Comparing.haveEqualElements(targetScope.dirtyPaths(),
                                                    listOf(getFilePath(root, "dir1"), getFilePath(root, "dir2"), getFilePath(root, "dir4"),
                                                           getFilePath(root, "dir3/file"))))
  }
}