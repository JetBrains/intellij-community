// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.hints.compilerPlugins

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.testFramework.rules.TempDirectory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.base.psi.userDataCached
import org.jetbrains.kotlin.idea.fir.extensions.CompilerPluginManifestAttributes
import org.jetbrains.kotlin.idea.fir.extensions.CompilerPluginRegistrarUtils
import org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPlugins
import org.jetbrains.kotlin.idea.resolve.KOTLINX_SERIALIZATION_CORE_JVM_MAVEN_COORDINATES
import org.jetbrains.kotlin.idea.resolve.loadSingleJarFromMaven
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addRoot
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.commons.ClassRemapper
import org.jetbrains.org.objectweb.asm.commons.Remapper
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.jar.Manifest
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import com.intellij.openapi.module.Module as OpenapiModule

@ApiStatus.Internal
@TestOnly
fun <T> OpenapiModule.withCompilerPlugin(
    plugin: KotlinK2BundledCompilerPlugins,
    options: String? = null,
    pluginJar: Path = plugin.bundledJarLocation,
    action: () -> T
): T {
    addPluginLibraryToClassPath(plugin)

    return withCustomCompilerOptions(
        "// COMPILER_ARGUMENTS: -Xplugin=${pluginJar.absolutePathString()} ${options?.let { "-P $it" }.orEmpty()}",
        project,
        module = this
    ) {
        action()
    }
}

/** Creates a "compatible" third-party compiler plugin jar as defined in [CompilerPluginManifestAttributes] */
@ApiStatus.Internal
@TestOnly
fun createThirdPartyCompilerPluginJar(
    plugin: KotlinK2BundledCompilerPlugins,
    tempDir: TempDirectory,
): Path = createThirdPartyCompilerPluginJar(plugin, tempDir.rootPath)

/** Creates a "compatible" third-party compiler plugin jar in [targetDir], as defined in [CompilerPluginManifestAttributes] */
@ApiStatus.Internal
@TestOnly
fun createThirdPartyCompilerPluginJar(
    plugin: KotlinK2BundledCompilerPlugins,
    targetDir: Path,
): Path {
    val pluginJar = plugin.bundledJarLocation
    val newPluginJar = pluginJar.copyTo(targetDir / pluginJar.fileName.pathString)

    FileSystems.newFileSystem(newPluginJar).use {
        val root = it.rootDirectories.single()
        val manifestPath = root / "META-INF" / "MANIFEST.MF"
        val newManifest = manifestPath.inputStream().use(::Manifest).apply {
            mainAttributes[CompilerPluginManifestAttributes.SINCE_BUILD_ATTR] = ApplicationInfo.getInstance().build.toString()
        }
        manifestPath.outputStream().use(newManifest::write)

        // Rename Registrar class so bundled plugin mechanism doesn't kick in
        val registrarClass = plugin.registrarClassName
        val registrarClassPackage = registrarClass.substringBeforeLast(".")
        val registrarClassName = registrarClass.substringAfterLast(".")
        val registrarClassNewName = "Renamed$registrarClassName"
        val registrarClassDir = registrarClassPackage.replace(".", "/")

        val registrarClassFile = root / registrarClassDir / "$registrarClassName.class"
        val registrarClassNewFile = root / registrarClassDir / "$registrarClassNewName.class"

        val registrarServiceFile = root / CompilerPluginRegistrarUtils.RegistrarFile.DEFAULT.location
        registrarServiceFile.writeText("$registrarClassPackage.$registrarClassNewName")

        val reader = ClassReader(registrarClassFile.readBytes())
        val writer = ClassWriter(0)
        val remapper = ClassRemapper(writer, object : Remapper() {
            override fun map(internalName: String): String {
                return if (internalName == "$registrarClassDir/$registrarClassName") {
                    "$registrarClassDir/$registrarClassNewName"
                } else {
                    super.map(internalName)
                }
            }
        })
        reader.accept(remapper, 0)
        registrarClassNewFile.writeBytes(writer.toByteArray())
    }

    return newPluginJar
}

internal fun OpenapiModule.addPluginLibraryToClassPath(plugin: KotlinK2BundledCompilerPlugins) {
    when (plugin) {
        KotlinK2BundledCompilerPlugins.KOTLINX_SERIALIZATION_COMPILER_PLUGIN -> {
            val serializationCoreJar = project.serializationCoreJar
            ConfigLibraryUtil.addLibrary(this, "serialization-core") {
                addRoot(serializationCoreJar, OrderRootType.CLASSES)
            }
        }

        else -> {}
    }
}

private val Project.serializationCoreJar: File by userDataCached("KOTLINX_SERIALIZATION_FILE", { 0 }) { project: Project ->
    project.loadSingleJarFromMaven(KOTLINX_SERIALIZATION_CORE_JVM_MAVEN_COORDINATES)
}
