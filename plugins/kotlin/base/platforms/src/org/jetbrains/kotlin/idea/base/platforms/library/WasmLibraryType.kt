// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.platforms.library

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.DummyLibraryProperties
import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.platforms.KotlinWasmLibraryKind
import javax.swing.JComponent

class WasmLibraryType : LibraryType<DummyLibraryProperties>(KotlinWasmLibraryKind) {
    override fun createPropertiesEditor(editorComponent: LibraryEditorComponent<DummyLibraryProperties>) = null

    @Suppress("HardCodedStringLiteral")
    override fun getCreateActionName() = "Kotlin/Wasm"

    override fun createNewLibrary(
      parentComponent: JComponent,
      contextDirectory: VirtualFile?,
      project: Project
    ): Nothing? = null

    override fun getIcon(properties: DummyLibraryProperties?) = KotlinIcons.JS
}