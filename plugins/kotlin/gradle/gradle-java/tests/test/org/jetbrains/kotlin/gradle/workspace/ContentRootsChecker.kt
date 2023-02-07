// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.SourceFolder
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.gradle.newTests.TestConfiguration
import org.jetbrains.kotlin.gradle.newTests.testFeatures.ContentRootsChecksConfiguration
import org.jetbrains.kotlin.gradle.workspace.PrinterRootType.*
import org.jetbrains.kotlin.gradle.workspace.PrinterRootType.RegularRoot.*
import java.io.File
import java.nio.file.Path
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
            .mapNotNull { it.toPrinterEntity(projectRoot) }

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
}

private sealed class PrinterRootType(val description: String) {

    sealed class RegularRoot(
        description: String,
        val owner: Owner,
        val isTest: Boolean,
        val isResources: Boolean /* false means it's sources */
    ) : PrinterRootType(description) {
        object Kotlin {
            object MainSources : RegularRoot("", Owner.Kotlin, isTest = false, isResources = false)
            object TestSources : RegularRoot("Test", Owner.Kotlin, isTest = true, isResources = false)

            object MainResources : RegularRoot("Resources", Owner.Kotlin, isTest = false, isResources = true)
            object TestResources : RegularRoot("Test, Resources", Owner.Kotlin, isTest = true, isResources = true)
        }

        object Java {
            object MainSources : RegularRoot("Java", Owner.Java, isTest = false, isResources = false)
            object TestSources : RegularRoot("Java, Test", Owner.Java, isTest = true, isResources = false)

            object MainResources : RegularRoot("Java, Resources", Owner.Java, isTest = false, isResources = true)
            object TestResources : RegularRoot("Java, Test, Resources", Owner.Java, isTest = true, isResources = true)
        }

        enum class Owner {
            Kotlin,
            Java,
        }
    }

    // Android-specific folders
    class Android(folderName: String) : PrinterRootType("Android, $folderName") {
        companion object {
            val ANDROID_SPECIFIC_FOLDER_NAMES = setOf(
                "shaders", "rs", "aidl"
            )
        }
    }

    object Generated : PrinterRootType("Generated")

    class Other(description: String) : PrinterRootType(description)
}

private class SourceFolderPrinterEntity(val pathRelativeToRoot: Path, val rootType: PrinterRootType) {
    override fun toString(): String {
        val rootTypeDescriptionIfNecessary = if (rootType.description.isNotEmpty())
            " (${rootType.description})"
        else
            ""

        return "$pathRelativeToRoot$rootTypeDescriptionIfNecessary"
    }
}

private fun SourceFolder.toPrinterEntity(projectRoot: File): SourceFolderPrinterEntity? {
    val path = file?.toNioPath() ?: return null

    val rootType = printerRootType()

    return SourceFolderPrinterEntity(projectRoot.toPath().relativize(path), rootType)
}

private fun SourceFolder.printerRootType(): PrinterRootType {
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
