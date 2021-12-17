// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.versions

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import org.jetbrains.kotlin.idea.KotlinPluginUtil
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import java.nio.file.Path

/**
 * Component forces update for built-in libraries in plugin directory. They are ignored because of
 * com.intellij.util.indexing.FileBasedIndex.isUnderConfigOrSystem()
 */
internal class KotlinUpdatePluginStartupActivity : StartupActivity.DumbAware {
    init {
        if (isUnitTestMode()) {
            throw ExtensionNotApplicableException.create()
        }
    }

    override fun runActivity(project: Project) {
        val propertiesComponent = PropertiesComponent.getInstance()
        val installedKotlinVersion = propertiesComponent.getValue(INSTALLED_KOTLIN_VERSION)

        if (KotlinPluginUtil.getPluginVersion() != installedKotlinVersion) {
            // Force refresh jar handlers
            KotlinArtifacts.instance.kotlincLibDirectory.listFiles()
                ?.filter { it.extension == "jar" }
                ?.forEach {
                    requestFullJarUpdate(it.toPath())
                }

            propertiesComponent.setValue(INSTALLED_KOTLIN_VERSION, KotlinPluginUtil.getPluginVersion())
        }
    }

    private fun requestFullJarUpdate(jarFilePath: Path) {
        val localVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(jarFilePath) ?: return

        // Build and update JarHandler
        val jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(localVirtualFile) ?: return
        VfsUtilCore.visitChildrenRecursively(jarFile, object : VirtualFileVisitor<Any?>() {})
        (jarFile as NewVirtualFile).markDirtyRecursively()
    }

    companion object {
        private const val INSTALLED_KOTLIN_VERSION = "installed.kotlin.plugin.version"
    }
}
