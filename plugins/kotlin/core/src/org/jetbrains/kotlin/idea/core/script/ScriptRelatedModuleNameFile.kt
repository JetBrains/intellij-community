/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.util.*
import org.jetbrains.kotlin.idea.util.application.getServiceSafe
import java.io.DataInputStream
import java.io.DataOutputStream

@Service
class ScriptRelatedModuleNameFile: AbstractFileAttributePropertyService<String>(
    name = "kotlin-script-moduleName",
    version = 1,
    read = DataInputStream::readString,
    write = DataOutputStream::writeString
) {
    companion object {
        operator fun get(project: Project, file: VirtualFile) = project.getServiceSafe<ScriptRelatedModuleNameFile>()[file]

        operator fun set(project: Project, file: VirtualFile, newValue: String?) {
            project.getServiceSafe<ScriptRelatedModuleNameFile>()[file] = newValue
        }
    }
}