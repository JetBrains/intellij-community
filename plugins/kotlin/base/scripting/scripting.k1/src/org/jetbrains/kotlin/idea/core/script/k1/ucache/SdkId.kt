// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1.ucache

import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Safe key for locating sdk in intellij project jdk table
 *
 * null means default sdk
 */
class SdkId private constructor(val homeDirectory: String?) {
    companion object {
        val default = SdkId(null as String?)

        operator fun invoke(homeDirectory: Path?): SdkId {
            if (homeDirectory == null || !homeDirectory.exists()) return default
            val canonicalPath = FileUtil.toSystemIndependentName(homeDirectory.toRealPath().toString())
            return SdkId(canonicalPath)
        }

        operator fun invoke(homeDirectory: String?): SdkId {
            if (homeDirectory == null) return default
            return invoke(Path.of(homeDirectory))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SdkId

        return homeDirectory == other.homeDirectory
    }

    override fun hashCode(): Int {
        return homeDirectory?.hashCode() ?: 0
    }
}