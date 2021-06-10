// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.configuration.klib

import org.jetbrains.kotlin.idea.KotlinPluginInternalApi
import java.io.File

@KotlinPluginInternalApi
interface KlibInfoProvider {

    fun getKlibInfo(libraryFile: File): KlibInfo?

    companion object {
        fun create(kotlinNativeHome: File): KlibInfoProvider {
            return DefaultKlibInfoProvider(kotlinNativeHome)
        }
    }
}

