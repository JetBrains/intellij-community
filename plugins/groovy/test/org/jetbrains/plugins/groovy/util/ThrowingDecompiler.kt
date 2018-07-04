/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.compiled.ClassFileDecompilers
import com.intellij.testFramework.PlatformTestUtil

object ThrowingDecompiler : ClassFileDecompilers.Light() {

  @JvmStatic
  fun disableDecompilers(parentDisposable: Disposable) {
    PlatformTestUtil.registerExtension(ClassFileDecompilers.EP_NAME, this, parentDisposable)
  }

  override fun accepts(file: VirtualFile): Boolean = true

  override fun getText(file: VirtualFile): CharSequence = throw UnsupportedOperationException("Decompilation requested for $file")
}
