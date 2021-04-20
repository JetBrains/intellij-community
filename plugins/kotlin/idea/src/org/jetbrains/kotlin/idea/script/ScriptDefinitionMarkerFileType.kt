/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.script

import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.scripting.definitions.SCRIPT_DEFINITION_MARKERS_PATH

object ScriptDefinitionMarkerFileType: FakeFileType() {
    private val markerPath = FileUtil.toCanonicalPath(SCRIPT_DEFINITION_MARKERS_PATH)

    override fun getName(): String = "script-definition-marker"

    // doesn't make sense for fake file types
    override fun getDescription(): String = name

    override fun getDefaultExtension(): String = ""

    override fun isMyFileType(file: VirtualFile): Boolean =
        FileUtil.toCanonicalPath(file.parent?.path)?.endsWith(markerPath) == true
}