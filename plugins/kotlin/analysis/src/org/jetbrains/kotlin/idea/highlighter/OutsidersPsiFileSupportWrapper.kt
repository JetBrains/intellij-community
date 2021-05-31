// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.OutsidersPsiFileSupport
import com.intellij.openapi.vfs.VirtualFile

object OutsidersPsiFileSupportWrapper {
    fun isOutsiderFile(virtualFile: VirtualFile): Boolean {
        return OutsidersPsiFileSupport.isOutsiderFile(virtualFile)
    }

    fun getOriginalFilePath(virtualFile: VirtualFile): String? {
        return OutsidersPsiFileSupport.getOriginalFilePath(virtualFile)
    }
}