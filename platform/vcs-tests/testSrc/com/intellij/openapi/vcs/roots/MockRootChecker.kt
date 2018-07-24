// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.roots

import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class MockRootChecker(private val vcs: MockAbstractVcs) : VcsRootChecker() {
  private val ignoredDirs = mutableSetOf<VirtualFile>()

  override fun getSupportedVcs() = vcs.keyInstanceMethod!!

  override fun isRoot(path: String) = File(path, VcsRootBaseTest.DOT_MOCK).exists()

  override fun isVcsDir(path: String) = path.toLowerCase().endsWith(VcsRootBaseTest.DOT_MOCK)

  override fun isIgnored(root: VirtualFile, checkForIgnore: VirtualFile) = ignoredDirs.contains(checkForIgnore);

  fun setIgnored(dirToIgnore: VirtualFile) = ignoredDirs.add(dirToIgnore);
}
