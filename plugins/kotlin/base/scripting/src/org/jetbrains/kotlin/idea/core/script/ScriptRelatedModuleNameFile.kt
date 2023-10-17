// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.util.AbstractFileGistService
import org.jetbrains.kotlin.idea.core.util.readString
import org.jetbrains.kotlin.idea.core.util.writeString
import java.io.DataInput
import java.io.DataOutput

@Service(Service.Level.PROJECT)
class ScriptRelatedModuleNameFile: AbstractFileGistService<String>(
    name = "kotlin-script-moduleName",
    version = 1,
    read = DataInput::readString,
    write = DataOutput::writeString
) {
    companion object {
        operator fun get(project: Project, file: VirtualFile): String? {
            return project.service<ScriptRelatedModuleNameFile>()[file]
        }

        operator fun set(project: Project, file: VirtualFile, newValue: String?) {
            project.service<ScriptRelatedModuleNameFile>()[file] = newValue
        }
    }
}