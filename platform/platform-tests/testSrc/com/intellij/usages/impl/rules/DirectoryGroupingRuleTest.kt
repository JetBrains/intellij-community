// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl.rules

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
internal class DirectoryGroupingRuleTest {
  @JvmField
  @RegisterExtension
  val projectModel = ProjectModelExtension()

  @Test
  fun `base directory belongs to the usage path in a multi-root project`() {
    val module = projectModel.createModule()
    val firstRoot = projectModel.baseProjectDir.newVirtualDirectory("firstRoot")
    val usageRoot = projectModel.baseProjectDir.newVirtualDirectory("usageRoot")
    ModuleRootModificationUtil.addContentRoot(module, firstRoot)
    ModuleRootModificationUtil.addContentRoot(module, usageRoot)
    val usageDirectory = projectModel.baseProjectDir.newVirtualDirectory("usageRoot/res/values")

    val baseDirectory = TestDirectoryGroupingRule(projectModel.project).findBaseDirectory(usageDirectory)

    assertSame(usageRoot, baseDirectory,
               "Directory grouping must make a usage path relative to the base directory which contains it")
  }
}

/** Exposes base-directory selection for a focused grouping-rule test. */
private class TestDirectoryGroupingRule(project: Project) : DirectoryGroupingRule(project) {
  /** Returns the base directory that production grouping will use for the supplied directory. */
  fun findBaseDirectory(directory: VirtualFile): VirtualFile? = baseDirectoryFor(directory)
}
