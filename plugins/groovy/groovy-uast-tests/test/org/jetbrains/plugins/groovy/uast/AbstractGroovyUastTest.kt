/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.uast

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.uast.test.common.RenderLogTestBase
import org.jetbrains.uast.test.env.AbstractUastFixtureTest
import java.io.File

abstract class AbstractGroovyUastTest : AbstractUastFixtureTest() {
  protected companion object {
    val TEST_GROOVY_MODEL_DIR = File(PathManagerEx.getCommunityHomePath(), "plugins/groovy/groovy-uast-tests/testData")
  }

  override fun getVirtualFile(testName: String): VirtualFile {
    val localPath = File(TEST_GROOVY_MODEL_DIR, testName).path
    val vFile = LocalFileSystem.getInstance().findFileByPath(localPath)
    return vFile ?: throw IllegalStateException("Couldn't find virtual file for $localPath")
  }
}

abstract class AbstractGroovyRenderLogTest : AbstractGroovyUastTest(), RenderLogTestBase {
  override fun getTestFile(testName: String, ext: String) =
    File(File(TEST_GROOVY_MODEL_DIR, testName).canonicalPath.substringBeforeLast('.') + '.' + ext)

}