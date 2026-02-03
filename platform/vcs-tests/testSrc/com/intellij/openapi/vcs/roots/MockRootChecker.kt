// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.roots

import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files

internal class MockRootChecker(private val vcs: MockAbstractVcs) : VcsRootChecker() {
  private val ignoredDirs = HashSet<VirtualFile>()

  override fun getSupportedVcs() = vcs.keyInstanceMethod!!

  override fun isRoot(file: VirtualFile) = Files.exists(file.toNioPath().resolve(DOT_MOCK))

  override fun isVcsDir(dirName: String) = dirName.equals(DOT_MOCK, ignoreCase = true)

  override fun isIgnored(root: VirtualFile, checkForIgnore: VirtualFile) = ignoredDirs.contains(checkForIgnore)

  fun setIgnored(dirToIgnore: VirtualFile) = ignoredDirs.add(dirToIgnore)
}
