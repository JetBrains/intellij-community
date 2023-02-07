// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.contentRoots

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.SourceFolder
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.kotlin.config.ResourceKotlinRootType
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestResourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.gradle.newTests.TestConfiguration
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.contentRoots.CheckerContentRootType.*
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.contentRoots.CheckerContentRootType.RegularRoot.Java
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.contentRoots.CheckerContentRootType.RegularRoot.Kotlin
import org.jetbrains.kotlin.gradle.newTests.workspace.PrinterContext
import org.jetbrains.kotlin.gradle.newTests.workspace.WorkspaceModelChecker
import org.jetbrains.kotlin.gradle.newTests.workspace.indented
import java.io.File
import kotlin.io.path.name

object ContentRootsChecker : WorkspaceModelChecker<ContentRootsChecksConfiguration>() {
    override fun createDefaultConfiguration(): ContentRootsChecksConfiguration = ContentRootsChecksConfiguration()

    override val classifier: String = "source-roots"

    /**
     * Only source folders are supported at the moment
     */
    override fun PrinterContext.process(module: Module) {
        val sourceFolders = runReadAction { ModuleRootManager.getInstance(module).contentEntries }
            .flatMap { it.sourceFolders.asList() }
            .mapNotNull { it.toCheckerEntity(projectRoot) }

        val configuration = testConfiguration.getConfiguration(ContentRootsChecker)

        val filteredSourceFolders = sourceFolders.asSequence()
            .filterNot {
                configuration.hideTestSourceRoots && (it.rootType as? RegularRoot)?.isTest == true ||
                        configuration.hideResourceRoots && (it.rootType as? RegularRoot)?.isResources == true ||
                        configuration.hideGeneratedRoots && it.rootType is Generated ||
                        configuration.hideAndroidSpecificRoots && it.rootType is Android
            }
            .toList()

        if (filteredSourceFolders.isEmpty()) return

        printer.indented {
            filteredSourceFolders.map { it.toString() }.sorted().forEach {
                printer.println(it)
            }
        }
    }

    override fun renderTestConfigurationDescription(testConfiguration: TestConfiguration): List<String> {
        val configuration = testConfiguration.getConfiguration(ContentRootsChecker)
        val hiddenSourceRoots = listOfNotNull(
            "tests".takeIf { configuration.hideTestSourceRoots },
            "resources".takeIf { configuration.hideResourceRoots },
            "android-specific roots".takeIf { configuration.hideAndroidSpecificRoots },
            "generated".takeIf { configuration.hideGeneratedRoots },
        )

        return if (hiddenSourceRoots.isEmpty())
            emptyList()
        else
            listOf("hiding following roots: ${hiddenSourceRoots.joinToString()}")
    }

    private fun SourceFolder.toCheckerEntity(projectRoot: File): SourceFolderCheckerEntity? {
        val path = file?.toNioPath() ?: return null

        val rootType = checkerRootType()

        return SourceFolderCheckerEntity(projectRoot.toPath().relativize(path), rootType)
    }

    private fun SourceFolder.checkerRootType(): CheckerContentRootType {
        val path = file?.toNioPath() ?: error("Unexpected SourceFolder $this without VirtualFile")

        // 1. Check generated folders
        if (path.toString().contains("build/generated")) return Generated

        // 2. Check Android-specific folders
        if (path.name in Android.ANDROID_SPECIFIC_FOLDER_NAMES) return Android(path.name)

        // 3. Check Kotlin folders
        when (rootType) {
            is SourceKotlinRootType -> return Kotlin.MainSources
            is TestSourceKotlinRootType -> return Kotlin.TestSources

            is ResourceKotlinRootType -> return Kotlin.MainResources
            is TestResourceKotlinRootType -> return Kotlin.TestResources

            JavaSourceRootType.SOURCE -> return Java.MainSources
            JavaSourceRootType.TEST_SOURCE -> return Java.TestSources

            JavaResourceRootType.RESOURCE -> return Java.MainResources
            JavaResourceRootType.TEST_RESOURCE -> return Java.TestResources
        }

        // 4. Fallback
        return Other(rootType.toString())
    }
}
