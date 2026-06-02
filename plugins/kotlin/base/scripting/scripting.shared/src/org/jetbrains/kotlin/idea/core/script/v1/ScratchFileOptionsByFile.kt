// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.v1

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.v1.ScratchFileOptionsByFile.Companion.set

@Service(Service.Level.PROJECT)
class ScratchFileOptionsByFile : AbstractFileGistService<ScratchFileOptions>(
    name = "kotlin-scratch-file-options",
    version = 2,
    read = {
        ScratchFileOptions(
            isRepl = readBoolean(),
            isMakeBeforeRun = readBoolean(),
            isInteractiveMode = readBoolean(),
            isExplainEnabled = readBoolean(),
            selectedJdkHome = readNullable { readString() },
            selectedModule = readNullable { readString() },
        )
    },
    write = { options ->
        writeBoolean(options.isRepl)
        writeBoolean(options.isMakeBeforeRun)
        writeBoolean(options.isInteractiveMode)
        writeBoolean(options.isExplainEnabled)
        writeNullable(options.selectedJdkHome) { writeString(it) }
        writeNullable(options.selectedModule) { writeString(it) }
    }
) {
    companion object {
        @JvmStatic
        operator fun get(project: Project, file: VirtualFile): ScratchFileOptions {
            return project.service<ScratchFileOptionsByFile>()[file] ?: ScratchFileOptions()
        }

        @JvmStatic
        operator fun set(project: Project, file: VirtualFile, newValue: ScratchFileOptions?) {
            project.service<ScratchFileOptionsByFile>()[file] = newValue
        }

        fun update(project: Project, file: VirtualFile, update: ScratchFileOptions.() -> ScratchFileOptions) {
            ScratchFileOptionsByFile[project, file] = ScratchFileOptionsByFile[project, file].update()
        }
    }
}

data class ScratchFileOptions(
    val isRepl: Boolean = false,
    val isMakeBeforeRun: Boolean = true,
    val isInteractiveMode: Boolean = false,
    val isExplainEnabled: Boolean = true,
    val selectedJdkHome: String? = null,
    val selectedModule: String? = null,
)
