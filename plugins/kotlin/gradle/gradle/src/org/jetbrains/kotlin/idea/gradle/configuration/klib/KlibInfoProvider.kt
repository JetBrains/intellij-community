// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradle.configuration.klib

import com.intellij.openapi.util.IntellijInternalApi
import java.io.File

@IntellijInternalApi
interface KlibInfoProvider {

    fun getKlibInfo(libraryFile: File): KlibInfo?

    companion object {
        fun create(kotlinNativeHome: File): KlibInfoProvider {
            return DefaultKlibInfoProvider(kotlinNativeHome)
        }
    }
}

