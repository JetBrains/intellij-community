// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.externalSystem.util.*
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import org.gradle.util.GradleVersion

abstract class GradleTestCase : GradleBaseTestCase() {

  fun isGradleAtLeast(version: String): Boolean =
    gradleFixture.gradleVersion.baseVersion >= GradleVersion.version(version)

  fun isGradleOlderThan(version: String): Boolean =
    gradleFixture.gradleVersion.baseVersion < GradleVersion.version(version)

  fun findOrCreateFile(relativePath: String, text: String): VirtualFile {
    gradleFixture.fileFixture.snapshot(relativePath)
    return runWriteActionAndGet {
      val file = gradleFixture.fileFixture.root.findOrCreateFile(relativePath)
      file.text = text
      FileDocumentManager.getInstance().getDocument(file)?.let {
        PsiDocumentManager.getInstance(gradleFixture.project).commitDocument(it)
      }
      file
    }
  }

  fun getFile(relativePath: String): VirtualFile {
    return runReadAction {
      gradleFixture.fileFixture.root.getFile(relativePath)
    }
  }
}