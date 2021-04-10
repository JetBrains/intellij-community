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
