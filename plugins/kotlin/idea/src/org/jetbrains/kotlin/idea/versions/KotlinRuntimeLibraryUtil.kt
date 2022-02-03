// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.versions

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.MultiMap
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ScalarIndexExtension
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPathsProvider
import org.jetbrains.kotlin.idea.util.isSnapshot
import org.jetbrains.kotlin.idea.util.projectStructure.version
import org.jetbrains.kotlin.idea.util.runReadActionInSmartMode
import org.jetbrains.kotlin.idea.vfilefinder.KotlinJavaScriptMetaFileIndex
import org.jetbrains.kotlin.idea.vfilefinder.hasSomethingInPackage
import org.jetbrains.kotlin.metadata.deserialization.BinaryVersion
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmMetadataVersion
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.JsMetadataVersion
import org.jetbrains.kotlin.utils.PathUtil

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

enum class LibraryJarDescriptor(val mavenArtifactId: String) {
    STDLIB_JAR(PathUtil.KOTLIN_JAVA_STDLIB_NAME),
    REFLECT_JAR(PathUtil.KOTLIN_JAVA_REFLECT_NAME),
    SCRIPT_RUNTIME_JAR(PathUtil.KOTLIN_JAVA_SCRIPT_RUNTIME_NAME),
    TEST_JAR(PathUtil.KOTLIN_TEST_NAME),
    RUNTIME_JDK8_JAR(PathUtil.KOTLIN_JAVA_RUNTIME_JDK8_NAME),
    JS_STDLIB_JAR(PathUtil.JS_LIB_NAME);

    fun findExistingJar(library: Library): VirtualFile? =
        library.getFiles(OrderRootType.CLASSES).firstOrNull { it.name.startsWith(mavenArtifactId) }

    val repositoryLibraryProperties: RepositoryLibraryProperties get() =
        RepositoryLibraryProperties(KotlinPathsProvider.KOTLIN_MAVEN_GROUP_ID, mavenArtifactId, kotlinCompilerVersionShort(), true, emptyList())
}

@NlsSafe
fun bundledRuntimeVersion(): String = KotlinCompilerVersion.VERSION

/**
 * Bundled compiler version usually looks like: `1.5.0-release-759`.
 * `kotlinCompilerVersionShort` would return `1.5.0` in such case
 */
fun kotlinCompilerVersionShort() = KotlinCompilerVersion.VERSION.substringBefore("-release")

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