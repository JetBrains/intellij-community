// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.compiled.ClassFileDecompilers

object ThrowingDecompiler : ClassFileDecompilers.Light() {
  @JvmStatic
  fun disableDecompilers(parentDisposable: Disposable) {
    ClassFileDecompilers.EP_NAME.getPoint(null).registerExtension(this, parentDisposable)
  }

  override fun accepts(file: VirtualFile): Boolean = true

  override fun getText(file: VirtualFile): CharSequence = throw UnsupportedOperationException("Decompilation requested for $file")
}
