// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import org.gradle.util.GradleVersion

interface GradleTestFixture : FileTestFixture, IdeaProjectTestFixture {

  val gradleVersion: GradleVersion

  val projectRoot: VirtualFile

  /**
   * Warning: this function can break fixture caches
   */
  fun reloadProject()
}