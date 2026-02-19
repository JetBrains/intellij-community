// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

@Service
class PluginStartupApplicationService : Disposable {
    private var aliveFlagPath: String? = null

    @Synchronized
    fun getAliveFlagPath(): String {
        if (aliveFlagPath == null) {
            try {
                val flagFile = Files.createTempFile("kotlin-idea-", "-is-running")
                val file = flagFile.toFile()
                Disposer.register(this) { file.delete() }
                aliveFlagPath = flagFile.toAbsolutePath().toString()
            } catch (e: IOException) {
                aliveFlagPath = ""
            }
        }
        return aliveFlagPath!!
    }

    @Synchronized
    fun resetAliveFlag() {
        val flagFile = aliveFlagPath?.let(Path::of) ?: return

        try {
          if (flagFile.isRegularFile() && Files.deleteIfExists(flagFile)) {
            this.aliveFlagPath = null
          }
        } catch (ignored: IOException) {}
    }

    override fun dispose() {}

    companion object {
        @JvmStatic
        fun getInstance(): PluginStartupApplicationService = service()
    }
}