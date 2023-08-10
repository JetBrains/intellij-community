// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting

import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.util.isKotlinFileType

class KotlinProblemFileHighlightFilter : Condition<VirtualFile> {
    override fun value(file: VirtualFile): Boolean = file.isKotlinFileType()
}