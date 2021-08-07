// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.test

import com.intellij.openapi.application.PathManager
import java.io.File

object KotlinRoot {
    @JvmField
    val REPO: File = File(PathManager.getHomePath())

    @JvmField
    val DIR: File = REPO.resolve("community/plugins/kotlin").takeIf { it.exists() }
        ?: File(PathManager.getCommunityHomePath()).resolve("plugins/kotlin")
}
