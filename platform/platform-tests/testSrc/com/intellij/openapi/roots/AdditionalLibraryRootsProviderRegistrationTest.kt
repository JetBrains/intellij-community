// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.ui.UIUtil

class AdditionalLibraryRootsProviderRegistrationTest : HeavyPlatformTestCase() {
  fun `test register and unregister provider`() {
    val file = tempDir.createVirtualFile("a.txt")
    val index = ProjectFileIndex.getInstance(myProject)
    assertFalse(index.isInLibrarySource(file))
    val disposable = Disposer.newDisposable()
    try {
      registerProvider(file, disposable)
      assertTrue(index.isInLibrarySource(file))
    }
    finally {
      Disposer.dispose(disposable)
      UIUtil.dispatchAllInvocationEvents()
    }
    assertFalse(index.isInLibrarySource(file))
  }

  private fun registerProvider(sourceRoot: VirtualFile, disposable: Disposable) {
    AdditionalLibraryRootsProvider.EP_NAME.getPoint().registerExtension(object : AdditionalLibraryRootsProvider() {
      override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        return listOf(SyntheticLibrary.newImmutableLibrary(listOf(sourceRoot)))
      }
    }, disposable)
    UIUtil.dispatchAllInvocationEvents()
  }
}