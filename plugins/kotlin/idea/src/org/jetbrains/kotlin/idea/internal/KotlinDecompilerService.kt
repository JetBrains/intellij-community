// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.internal

import com.intellij.openapi.components.serviceOrNull
import org.jetbrains.kotlin.psi.KtFile

class DecompileFailedException(message: String, cause: Throwable) : RuntimeException(message, cause)

interface KotlinDecompilerService {
    fun decompile(file: KtFile): String?

    companion object {
        fun getInstance(): KotlinDecompilerService? = serviceOrNull()
    }
}

