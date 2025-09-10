// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass

public interface BytecodeViewerClassFileFinder {
  public fun findClass(element: PsiClass, containing: PsiClass?): VirtualFile?
}