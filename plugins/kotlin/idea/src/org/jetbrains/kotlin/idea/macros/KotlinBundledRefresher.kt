// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.macros

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries

/**
 * Component forces update for built-in libraries in plugin directory. They are ignored because of
 * com.intellij.util.indexing.FileBasedIndex.isUnderConfigOrSystem()
 */
internal class KotlinBundledRefresher : ProjectPostStartupActivity {
    init {
        if (isUnitTestMode()) {
            throw ExtensionNotApplicableException.create()
        }
    }

    override suspend fun execute(project: Project) {
        val propertiesComponent = PropertiesComponent.getInstance()
        val installedKotlinVersion = propertiesComponent.getValue(INSTALLED_KOTLIN_VERSION)

        if (KotlinIdePlugin.version != installedKotlinVersion) {
            // Force refresh jar handlers
            requestKotlinDistRefresh(KotlinPluginLayout.kotlinc.toPath())

            propertiesComponent.setValue(INSTALLED_KOTLIN_VERSION, KotlinIdePlugin.version)
        }
    }

    companion object {
        private const val INSTALLED_KOTLIN_VERSION = "installed.kotlin.plugin.version"

        private fun requestFullJarUpdate(jarFilePath: Path) {
            val localVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(jarFilePath) ?: return

            // Build and update JarHandler
            val jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(localVirtualFile) ?: return
            VfsUtilCore.visitChildrenRecursively(jarFile, object : VirtualFileVisitor<Any?>() {})
            (jarFile as NewVirtualFile).markDirtyRecursively()
        }

        fun requestKotlinDistRefresh(kotlinBundledPath: Path) {
            kotlinBundledPath.resolve("lib").also { require(it.exists()) { "kotlin-dist invalid dir layout" } }
                .listDirectoryEntries()
                .filter { it.extension == "jar" }
                .forEach { requestFullJarUpdate(it) }
        }
    }
}
