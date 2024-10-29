// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.platforms.library

import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.DummyLibraryProperties
import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.openapi.roots.libraries.LibraryTypeService
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import com.intellij.openapi.roots.libraries.ui.DescendentBasedRootFilter
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor
import com.intellij.openapi.roots.libraries.ui.RootDetector
import com.intellij.openapi.roots.ui.configuration.libraryEditor.DefaultLibraryRootsComponentDescriptor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.platforms.KotlinBasePlatformsBundle
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptLibraryKind
import javax.swing.Icon
import javax.swing.JComponent

class JSLibraryType : LibraryType<DummyLibraryProperties>(KotlinJavaScriptLibraryKind) {
    override fun createPropertiesEditor(editorComponent: LibraryEditorComponent<DummyLibraryProperties>): LibraryPropertiesEditor? {
        return null
    }

    @Suppress("HardCodedStringLiteral")
    override fun getCreateActionName(): String = "Kotlin/JS"

    override fun createNewLibrary(
        parentComponent: JComponent,
        contextDirectory: VirtualFile?,
        project: Project
    ): NewLibraryConfiguration? = LibraryTypeService.getInstance().createLibraryFromFiles(
        RootsComponentDescriptor,
        parentComponent, contextDirectory, this,
        project
    )

    override fun getIcon(properties: DummyLibraryProperties?): Icon = KotlinIcons.JS

    companion object {
        @Suppress("DEPRECATION")
        fun getInstance(): JSLibraryType = Extensions.findExtension(EP_NAME, JSLibraryType::class.java)
    }

    object RootsComponentDescriptor : DefaultLibraryRootsComponentDescriptor() {
        override fun createAttachFilesChooserDescriptor(libraryName: String?): FileChooserDescriptor {
            val descriptor = FileChooserDescriptor(true, true, true, false, true, true)
                .withExtensionFilter(ProjectBundle.message("library.attach.files.label"), "js", "kjsm")
                .withFileFilter { FileElement.isArchive(it) || isAcceptedForJsLibrary(it.extension) }
            descriptor.title = if (StringUtil.isEmpty(libraryName))
                ProjectBundle.message("library.attach.files.action")
            else
                ProjectBundle.message("library.attach.files.to.library.action", libraryName!!)
            descriptor.description = JavaUiBundle.message("library.java.attach.files.description")
            return descriptor
        }

        override fun getRootTypes(): Array<OrderRootType?> = arrayOf(OrderRootType.CLASSES, OrderRootType.SOURCES)

        override fun getRootDetectors(): List<RootDetector> = arrayListOf(
            DescendentBasedRootFilter(OrderRootType.CLASSES, false, KotlinBasePlatformsBundle.message("presentable.type.js.files")) {
                isAcceptedForJsLibrary(it.extension)
            },
            DescendentBasedRootFilter.createFileTypeBasedFilter(OrderRootType.SOURCES, false, KotlinFileType.INSTANCE, "sources")
        )

        private fun isAcceptedForJsLibrary(extension: String?): Boolean = extension == "js" || extension == "kjsm"
    }
}
