/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.test

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ArrayUtil
import java.io.File
import java.util.*

abstract class VcsPlatformTest : PlatformTestCase() {

  protected lateinit var myTestRoot: File
  protected lateinit var myTestRootFile: VirtualFile
  protected lateinit var myProjectRoot: VirtualFile
  protected lateinit var myProjectPath: String

  private lateinit var myTestStartedIndicator: String

  protected lateinit var changeListManager: ChangeListManagerImpl

  @Throws(Exception::class)
  override fun setUp() {
    myTestRoot = File(FileUtil.getTempDirectory(), "testRoot")
    PlatformTestCase.myFilesToDelete.add(myTestRoot)
    checkTestRootIsEmpty(myTestRoot)

    runInEdtAndWait { super@VcsPlatformTest.setUp() }
    myTestRootFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myTestRoot)!!
    refresh()

    myTestStartedIndicator = enableDebugLogging()

    myProjectRoot = myProject.baseDir
    myProjectPath = myProjectRoot.path

    changeListManager = ChangeListManager.getInstance(myProject) as ChangeListManagerImpl
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      clearFields(this)
    }
    finally {
      try {
        runInEdtAndWait { super@VcsPlatformTest.tearDown() }
      }
      finally {
        if (myAssertionsInTestDetected) {
          TestLoggerFactory.dumpLogToStdout(myTestStartedIndicator)
        }
      }
    }
  }

  /**
   * Returns log categories which will be switched to DEBUG level.
   * Implementations must add theirs categories to the ones from super class,
   * not to erase log categories from the super class.
   * (e.g. by calling `super.getDebugLogCategories().plus(additionalCategories)`.
   */
  protected open fun getDebugLogCategories(): Collection<String> = emptyList()

  override fun getIprFile(): File {
    val projectRoot = File(myTestRoot, "project")
    return FileUtil.createTempFile(projectRoot, name + "_", ProjectFileType.DOT_DEFAULT_EXTENSION)
  }

  override fun setUpModule() {
    // we don't need a module in Git tests
  }

  override fun runInDispatchThread(): Boolean {
    return false
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    var name = super.getTestName(lowercaseFirstLetter)
    name = StringUtil.shortenTextWithEllipsis(name.trim { it <= ' ' }.replace(" ", "_"), 12, 6, "_")
    if (name.startsWith("_")) {
      name = name.substring(1)
    }
    return name
  }

  protected inline fun wasInit(f: () -> Unit): Boolean {
    try {
      f()
    }
    catch(e: UninitializedPropertyAccessException) {
      return false
    }
    return true
  }

  protected open fun refresh() {
    VfsUtil.markDirtyAndRefresh(false, true, false, myTestRootFile)
  }

  protected fun updateChangeListManager() {
    val changeListManager = ChangeListManager.getInstance(myProject)
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty()
    changeListManager.ensureUpToDate(false)
  }

  private fun checkTestRootIsEmpty(testRoot: File) {
    val files = testRoot.listFiles()
    if (files != null && files.size > 0) {
      LOG.warn("Test root was not cleaned up during some previous test run. " + "testRoot: " + testRoot +
          ", files: " + Arrays.toString(files))
      for (file in files) {
        LOG.assertTrue(FileUtil.delete(file))
      }
    }
  }

  private fun enableDebugLogging(): String {
    TestLoggerFactory.enableDebugLogging(testRootDisposable, *ArrayUtil.toStringArray(getDebugLogCategories()))
    val testStartedIndicator = createTestStartedIndicator()
    LOG.info(testStartedIndicator)
    return testStartedIndicator
  }

  private fun createTestStartedIndicator(): String {
    return "Starting " + javaClass.name + "." + getTestName(false) + Math.random()
  }
}