// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.script

import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.scripting.definitions.SCRIPT_DEFINITION_MARKERS_PATH

object ScriptDefinitionMarkerFileType: FakeFileType() {
    private val markerPathComponents: Array<String> = SCRIPT_DEFINITION_MARKERS_PATH.split('/').filter { it.isNotEmpty() }.reversed().toTypedArray()
    val lastPathSegment: String
        get() = markerPathComponents.first()

    override fun getName(): String = "script-definition-marker"

    // doesn't make sense for fake file types
    override fun getDescription(): String = name

    override fun getDefaultExtension(): String = ""

    override fun isMyFileType(file: VirtualFile): Boolean =
      file.parent?.let(::isParentOfMyFileType) ?: false

    fun isParentOfMyFileType(file: VirtualFile): Boolean {
        var parentPath = file.takeIf { it.isDirectory } ?: return false
        for (pathComponent in markerPathComponents) {
            parentPath = parentPath.takeIf { it.name == pathComponent }?.parent ?: return false
        }
        return true
    }
}