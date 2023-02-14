// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.test

import com.intellij.openapi.application.PathManager
import java.io.File
import java.nio.file.Path

object KotlinRoot {
    @JvmField
    val REPO: File = File(PathManager.getHomePath()).canonicalFile

    @JvmField
    val DIR: File = REPO.resolve("community/plugins/kotlin").takeIf { it.exists() }
        ?: File(PathManager.getCommunityHomePath()).resolve("plugins/kotlin").canonicalFile

    @JvmField
    val PATH: Path = DIR.toPath()
}
