// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.roots

import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Paths

class MockRootChecker(private val vcs: MockAbstractVcs) : VcsRootChecker() {
  private val ignoredDirs = HashSet<VirtualFile>()

  override fun getSupportedVcs() = vcs.keyInstanceMethod!!

  override fun isRoot(path: String) = Files.exists(Paths.get(path, DOT_MOCK))

  override fun isVcsDir(dirName: String) = dirName.equals(DOT_MOCK, ignoreCase = true)

  override fun isIgnored(root: VirtualFile, checkForIgnore: VirtualFile) = ignoredDirs.contains(checkForIgnore)

  fun setIgnored(dirToIgnore: VirtualFile) = ignoredDirs.add(dirToIgnore)
}
