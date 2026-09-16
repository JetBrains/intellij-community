// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core

import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.impl.PsiTreeChangePreprocessorBackgroundable
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreProjectEnvironmentTest {
  @Test
  fun `the environment registers the backgroundable preprocessor extension point`() {
    Disposer.newDisposable().use { disposable ->
      val project = CoreProjectEnvironment(disposable, CoreApplicationEnvironment(disposable)).project
      val extensionPoint = project.extensionArea.getExtensionPoint<PsiTreeChangePreprocessorBackgroundable>(
        PsiTreeChangePreprocessorBackgroundable.EP.name)
      assertEquals(PsiTreeChangePreprocessorBackgroundable::class.java.name, (extensionPoint as ExtensionPointImpl<*>).className)

      val preprocessor = object : PsiTreeChangePreprocessorBackgroundable {
        override fun treeChanged(event: PsiTreeChangeEventImpl) {}
      }
      extensionPoint.registerExtension(preprocessor, disposable)
      assertEquals(listOf(preprocessor), PsiTreeChangePreprocessorBackgroundable.EP.getExtensions(project))
    }
  }
}
