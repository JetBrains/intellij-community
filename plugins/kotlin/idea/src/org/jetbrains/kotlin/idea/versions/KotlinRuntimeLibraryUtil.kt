// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.versions

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.MultiMap
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ScalarIndexExtension
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.framework.JavaRuntimeDetectionUtil
import org.jetbrains.kotlin.idea.framework.isExternalLibrary
import org.jetbrains.kotlin.idea.util.isSnapshot
import org.jetbrains.kotlin.idea.util.projectStructure.version
import org.jetbrains.kotlin.idea.util.runReadActionInSmartMode
import org.jetbrains.kotlin.idea.vfilefinder.KotlinJavaScriptMetaFileIndex
import org.jetbrains.kotlin.idea.vfilefinder.hasSomethingInPackage
import org.jetbrains.kotlin.metadata.deserialization.BinaryVersion
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmMetadataVersion
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.JsMetadataVersion
import org.jetbrains.kotlin.utils.LibraryUtils
import org.jetbrains.kotlin.utils.PathUtil
import java.nio.file.Path

fun getLibraryRootsWithAbiIncompatibleKotlinClasses(module: Module): Collection<BinaryVersionedFile<JvmMetadataVersion>> {
    return getLibraryRootsWithAbiIncompatibleVersion(module, JvmMetadataVersion.INSTANCE, KotlinJvmMetadataVersionIndex)
}

fun getLibraryRootsWithAbiIncompatibleForKotlinJs(module: Module): Collection<BinaryVersionedFile<JsMetadataVersion>> {
    return getLibraryRootsWithAbiIncompatibleVersion(module, JsMetadataVersion.INSTANCE, KotlinJsMetadataVersionIndex)
}

fun findAllUsedLibraries(project: Project): MultiMap<Library, Module> {
    val libraries = MultiMap<Library, Module>()

    for (module in ModuleManager.getInstance(project).modules) {
        val moduleRootManager = ModuleRootManager.getInstance(module)

        for (entry in moduleRootManager.orderEntries.filterIsInstance<LibraryOrderEntry>()) {
            val library = entry.library ?: continue

            libraries.putValue(library, module)
        }
    }

    return libraries
}

enum class LibraryJarDescriptor(
    val jarName: String,
    val orderRootType: OrderRootType,
    val shouldExist: Boolean,
    val getPath: (KotlinArtifacts) -> Path = { artifacts -> artifacts.kotlincLibDirectory.toPath().resolve(jarName) }
) {
    RUNTIME_JAR(PathUtil.KOTLIN_JAVA_STDLIB_JAR, OrderRootType.CLASSES, true, { it.kotlinStdlib.toPath() }) {
        override fun findExistingJar(library: Library): VirtualFile? {
            if (isExternalLibrary(library)) return null
            return JavaRuntimeDetectionUtil.getRuntimeJar(listOf(*library.getFiles(OrderRootType.CLASSES)))
        }
    },

    REFLECT_JAR(PathUtil.KOTLIN_JAVA_REFLECT_JAR, OrderRootType.CLASSES, false, { it.kotlinReflect.toPath() }),
    SCRIPT_RUNTIME_JAR(PathUtil.KOTLIN_JAVA_SCRIPT_RUNTIME_JAR, OrderRootType.CLASSES, true, { it.kotlinScriptRuntime.toPath() }),
    TEST_JAR(PathUtil.KOTLIN_TEST_JAR, OrderRootType.CLASSES, false, { it.kotlinTest.toPath() }),

    @Deprecated("RUNTIME_JDK7_JAR should be used since 1.2")
    RUNTIME_JRE7_JAR(PathUtil.KOTLIN_JAVA_RUNTIME_JRE7_JAR, OrderRootType.CLASSES, false),
    RUNTIME_JDK7_JAR(PathUtil.KOTLIN_JAVA_RUNTIME_JDK7_JAR, OrderRootType.CLASSES, false),

    @Deprecated("RUNTIME_JDK8_JAR should be used since 1.2")
    RUNTIME_JRE8_JAR(PathUtil.KOTLIN_JAVA_RUNTIME_JRE8_JAR, OrderRootType.CLASSES, false),
    RUNTIME_JDK8_JAR(PathUtil.KOTLIN_JAVA_RUNTIME_JDK8_JAR, OrderRootType.CLASSES, false),

    @Deprecated("RUNTIME_JDK7_SOURCES_JAR should be used since 1.2")
    RUNTIME_JRE7_SOURCES_JAR(PathUtil.KOTLIN_JAVA_RUNTIME_JRE7_SRC_JAR, OrderRootType.SOURCES, false),
    RUNTIME_JDK7_SOURCES_JAR(PathUtil.KOTLIN_JAVA_RUNTIME_JDK7_SRC_JAR, OrderRootType.SOURCES, false),

    @Deprecated("RUNTIME_JDK8_SOURCES_JAR should be used since 1.2")
    RUNTIME_JRE8_SOURCES_JAR(PathUtil.KOTLIN_JAVA_RUNTIME_JRE8_SRC_JAR, OrderRootType.SOURCES, false),
    RUNTIME_JDK8_SOURCES_JAR(PathUtil.KOTLIN_JAVA_RUNTIME_JDK8_SRC_JAR, OrderRootType.SOURCES, false),

    RUNTIME_SRC_JAR(PathUtil.KOTLIN_JAVA_STDLIB_SRC_JAR, OrderRootType.SOURCES, false, { it.kotlinStdlibSources.toPath() }) {
        override fun findExistingJar(library: Library): VirtualFile? {
            return super.findExistingJar(library) ?: LibraryUtils.getJarFile(
                library.getFiles(orderRootType).toList(),
                PathUtil.KOTLIN_JAVA_STDLIB_SRC_JAR_OLD
            )
        }
    },
    REFLECT_SRC_JAR(PathUtil.KOTLIN_REFLECT_SRC_JAR, OrderRootType.SOURCES, false),
    TEST_SRC_JAR(PathUtil.KOTLIN_TEST_SRC_JAR, OrderRootType.SOURCES, false),

    JS_STDLIB_JAR(PathUtil.JS_LIB_JAR_NAME, OrderRootType.CLASSES, true, { it.kotlinStdlibJs.toPath() }),
    JS_STDLIB_SRC_JAR(PathUtil.JS_LIB_SRC_JAR_NAME, OrderRootType.SOURCES, false, { it.kotlinStdlibJsSources.toPath() });

    open fun findExistingJar(library: Library): VirtualFile? {
        if (isExternalLibrary(library)) return null
        return LibraryUtils.getJarFile(listOf(*library.getFiles(orderRootType)), jarName)
    }

    fun getPathInPlugin() = getPath(KotlinArtifacts.instance)
}

@NlsSafe
fun bundledRuntimeVersion(): String = KotlinCompilerVersion.VERSION

private val KOTLIN_COMPILER_VERSION_SEPARATOR = "-(?:dev|release)".toRegex()

/**
 * Bundled compiler version usually looks like: `1.5.0-release-759`.
 * `kotlinCompilerVersionShort` would return `1.5.0` in such case
 */
fun kotlinCompilerVersionShort(): String {
    val parts = KOTLIN_COMPILER_VERSION_SEPARATOR.split(KotlinCompilerVersion.VERSION)
    return parts.first()
}

private val KOTLIN_COMPILER_VERSION_PATTERN = "(\\d+)\\.(\\d+)(?:\\.(\\d+))?.*".toRegex()

fun KotlinVersion.Companion.fromString(version: String): KotlinVersion? {
    val (major, minor, patch) = KOTLIN_COMPILER_VERSION_PATTERN.matchEntire(version)?.destructured ?: return null
    val majorValue = major.toIntOrNull() ?: return null
    val minorValue = minor.toIntOrNull() ?: return null
    val patchValue = patch.toIntOrNull() ?: 0
    return KotlinVersion(majorValue, minorValue, patchValue)
}

data class BinaryVersionedFile<out T : BinaryVersion>(val file: VirtualFile, val version: T, val supportedVersion: T)

private fun <T : BinaryVersion> getLibraryRootsWithAbiIncompatibleVersion(
    module: Module,
    supportedVersion: T,
    index: ScalarIndexExtension<T>
): Collection<BinaryVersionedFile<T>> {
    val id = index.name

    val moduleWithAllDependencies = setOf(module) + ModuleUtil.getAllDependentModules(module)
    val moduleWithAllDependentLibraries = GlobalSearchScope.union(
        moduleWithAllDependencies.map { it.moduleWithLibrariesScope }.toTypedArray()
    )

    val allVersions = FileBasedIndex.getInstance().getAllKeys(id, module.project)
    val badVersions = allVersions.filterNot(BinaryVersion::isCompatible).toHashSet()
    val badRoots = hashSetOf<BinaryVersionedFile<T>>()
    val fileIndex = ProjectFileIndex.SERVICE.getInstance(module.project)

    for (version in badVersions) {
        val indexedFiles = FileBasedIndex.getInstance().getContainingFiles(id, version, moduleWithAllDependentLibraries)
        for (indexedFile in indexedFiles) {
            val libraryRoot = fileIndex.getClassRootForFile(indexedFile) ?: error(
                "Only library roots were requested, and only class files should be indexed with the $id key. " +
                        "File: ${indexedFile.path}"
            )
            badRoots.add(BinaryVersionedFile(VfsUtil.getLocalFile(libraryRoot), version, supportedVersion))
        }
    }

    return badRoots
}

fun showRuntimeJarNotFoundDialog(project: Project, jarName: String) {
    Messages.showErrorDialog(
        project,
        KotlinBundle.message("version.dialog.message.is.not.found.make.sure.plugin.is.properly.installed", jarName),
        KotlinBundle.message("version.title.no.runtime.found")
    )
}

private val KOTLIN_JS_FQ_NAME = FqName("kotlin.js")

fun hasKotlinJsKjsmFile(project: Project, scope: GlobalSearchScope): Boolean {
    return project.runReadActionInSmartMode {
        KotlinJavaScriptMetaFileIndex.hasSomethingInPackage(KOTLIN_JS_FQ_NAME, scope)
    }
}

fun getStdlibArtifactId(sdk: Sdk?, version: String): String {
    if (!hasJreSpecificRuntime(version)) {
        return MAVEN_STDLIB_ID
    }

    val sdkVersion = sdk?.version
    if (hasJdkLikeUpdatedRuntime(version)) {
        return when {
            sdkVersion != null && sdkVersion.isAtLeast(JavaSdkVersion.JDK_1_8) -> MAVEN_STDLIB_ID_JDK8
            sdkVersion == JavaSdkVersion.JDK_1_7 -> MAVEN_STDLIB_ID_JDK7
            else -> MAVEN_STDLIB_ID
        }
    }

    return when {
        sdkVersion != null && sdkVersion.isAtLeast(JavaSdkVersion.JDK_1_8) -> MAVEN_STDLIB_ID_JRE8
        sdkVersion == JavaSdkVersion.JDK_1_7 -> MAVEN_STDLIB_ID_JRE7
        else -> MAVEN_STDLIB_ID
    }
}

fun getDefaultJvmTarget(sdk: Sdk?, version: String): JvmTarget? {
    if (!hasJreSpecificRuntime(version)) {
        return null
    }
    val sdkVersion = sdk?.version
    return when {
        sdkVersion == null -> null
        sdkVersion.isAtLeast(JavaSdkVersion.JDK_1_8) -> JvmTarget.JVM_1_8
        sdkVersion.isAtLeast(JavaSdkVersion.JDK_1_6) -> JvmTarget.JVM_1_6
        else -> null
    }
}

fun hasJdkLikeUpdatedRuntime(version: String): Boolean =
    VersionComparatorUtil.compare(version, "1.2.0-rc-39") >= 0 ||
            isSnapshot(version) ||
            version == "default_version" /* for tests */

fun hasJreSpecificRuntime(version: String): Boolean =
    VersionComparatorUtil.compare(version, "1.1.0") >= 0 ||
            isSnapshot(version) ||
            version == "default_version" /* for tests */

const val MAVEN_STDLIB_ID = PathUtil.KOTLIN_JAVA_STDLIB_NAME

const val MAVEN_STDLIB_ID_JRE7 = PathUtil.KOTLIN_JAVA_RUNTIME_JRE7_NAME
const val MAVEN_STDLIB_ID_JDK7 = PathUtil.KOTLIN_JAVA_RUNTIME_JDK7_NAME

const val MAVEN_STDLIB_ID_JRE8 = PathUtil.KOTLIN_JAVA_RUNTIME_JRE8_NAME
const val MAVEN_STDLIB_ID_JDK8 = PathUtil.KOTLIN_JAVA_RUNTIME_JDK8_NAME

const val MAVEN_JS_STDLIB_ID = PathUtil.JS_LIB_NAME
const val MAVEN_JS_TEST_ID = PathUtil.KOTLIN_TEST_JS_NAME


const val MAVEN_TEST_ID = PathUtil.KOTLIN_TEST_NAME
const val MAVEN_TEST_JUNIT_ID = "kotlin-test-junit"
const val MAVEN_COMMON_TEST_ID = "kotlin-test-common"
const val MAVEN_COMMON_TEST_ANNOTATIONS_ID = "kotlin-test-annotations-common"

val LOG = Logger.getInstance("org.jetbrains.kotlin.idea.versions.KotlinRuntimeLibraryUtilKt")

data class LibInfo(
    val groupId: String,
    val name: String,
    val version: String = "0.0.0"
)

data class DeprecatedLibInfo(
    val old: LibInfo,
    val new: LibInfo,
    val outdatedAfterVersion: String,
    @Nls val message: String
)

private fun deprecatedLib(
    oldGroupId: String,
    oldName: String,
    newGroupId: String = oldGroupId,
    newName: String = oldName,
    outdatedAfterVersion: String,
    @Nls message: String
): DeprecatedLibInfo {
    return DeprecatedLibInfo(
        old = LibInfo(groupId = oldGroupId, name = oldName),
        new = LibInfo(groupId = newGroupId, name = newName),
        outdatedAfterVersion = outdatedAfterVersion,
        message = message
    )
}

val DEPRECATED_LIBRARIES_INFORMATION = listOf(
    deprecatedLib(
        oldGroupId = "org.jetbrains.kotlin",
        oldName = PathUtil.KOTLIN_JAVA_RUNTIME_JRE7_NAME, newName = PathUtil.KOTLIN_JAVA_RUNTIME_JDK7_NAME,
        outdatedAfterVersion = "1.2.0-rc-39",
        message = KotlinBundle.message(
            "version.message.is.deprecated.since.1.2.0.and.should.be.replaced.with",
            PathUtil.KOTLIN_JAVA_RUNTIME_JRE7_NAME,
            PathUtil.KOTLIN_JAVA_RUNTIME_JDK7_NAME
        )
    ),

    deprecatedLib(
        oldGroupId = "org.jetbrains.kotlin",
        oldName = PathUtil.KOTLIN_JAVA_RUNTIME_JRE8_NAME, newName = PathUtil.KOTLIN_JAVA_RUNTIME_JDK8_NAME,
        outdatedAfterVersion = "1.2.0-rc-39",
        message = KotlinBundle.message(
            "version.message.is.deprecated.since.1.2.0.and.should.be.replaced.with",
            PathUtil.KOTLIN_JAVA_RUNTIME_JRE8_NAME,
            PathUtil.KOTLIN_JAVA_RUNTIME_JDK8_NAME
        )
    )
)