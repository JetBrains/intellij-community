// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.platforms.library

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.DummyLibraryProperties
import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.platforms.KotlinWasmJsLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.KotlinWasmWasiLibraryKind
import javax.swing.Icon
import javax.swing.JComponent

abstract class WasmLibraryType(libraryKind: PersistentLibraryKind<DummyLibraryProperties>) : LibraryType<DummyLibraryProperties>(libraryKind) {
    override fun createPropertiesEditor(editorComponent: LibraryEditorComponent<DummyLibraryProperties>): LibraryPropertiesEditor? = null

    override fun getCreateActionName(): Nothing? = null

    override fun createNewLibrary(
      parentComponent: JComponent,
      contextDirectory: VirtualFile?,
      project: Project
    ): Nothing? = null

    override fun getIcon(properties: DummyLibraryProperties?): Icon = KotlinIcons.JS
}

class WasmJsLibraryType : WasmLibraryType(KotlinWasmJsLibraryKind)

class WasmWasiLibraryType : WasmLibraryType(KotlinWasmWasiLibraryKind)