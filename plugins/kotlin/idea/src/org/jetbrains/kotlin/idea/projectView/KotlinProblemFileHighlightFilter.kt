// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.projectView

import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.KotlinFileType

class KotlinProblemFileHighlightFilter : Condition<VirtualFile> {
    override fun value(t: VirtualFile): Boolean = FileTypeRegistry.getInstance().isFileOfType(t, KotlinFileType.INSTANCE)
}
