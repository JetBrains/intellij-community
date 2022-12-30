@file:JvmName("FileUtils")
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.utils

import com.intellij.model.ModelBranchUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFileBase

fun findOriginalFile(file: VirtualFile?) =
  ModelBranchUtil.findOriginalFile(file)
    ?.let {
      var f: VirtualFile? = it
      while (f is LightVirtualFileBase) {
        f = f.originalFile
      }
      f
    }