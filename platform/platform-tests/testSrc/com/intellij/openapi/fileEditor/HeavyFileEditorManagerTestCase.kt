// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.fileEditor.impl.FileEditorManagerExImpl
import com.intellij.openapi.fileEditor.impl.overrideFileEditorManagerImplementation
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase

abstract class HeavyFileEditorManagerTestCase : CodeInsightFixtureTestCase<ModuleFixtureBuilder<*>?>() {
  protected fun getFile(path: String): VirtualFile? {
    return LocalFileSystem.getInstance()
      .refreshAndFindFileByPath("${PlatformTestUtil.getPlatformTestDataPath()}fileEditorManager$path")
  }

  override fun tuneFixture(moduleBuilder: ModuleFixtureBuilder<*>?) {
    overrideFileEditorManagerImplementation(FileEditorManagerExImpl::class.java, testRootDisposable)
  }

  override fun getBasePath(): String {
    return "/platform/platform-tests/testData/fileEditorManager"
  }
}