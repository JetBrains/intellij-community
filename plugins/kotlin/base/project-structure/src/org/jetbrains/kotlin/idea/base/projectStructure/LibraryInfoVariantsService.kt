// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptStdlibDetectorFacility
import org.jetbrains.kotlin.idea.base.platforms.KotlinJvmStdlibDetectorFacility
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.NativeKlibLibraryInfo
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi

/**
 * Stores mappings from [LibraryInfo] to all its variants. "Library variant" here means an imported library
 * which was published from the same Multiplatform module (Gradle subproject).
 *
 * For instance, a typical iOS/JVM/Linux library would have the following variants:
 * ```
 * com.example:lib-iosarm64:1.0
 * com.example:lib-iossimulatorarm64:1.0
 * com.example:lib-jvm:1.0
 * com.example:lib-linuxx64:1.0
 * com.example:lib:iosMain:1.0
 * com.example:lib:nativeMain:1.0
 * com.example:lib:commonMain:1.0
 * ```
 * Use with caution – variant calculation is based on a name heuristic (therefore not precise).
 * Only Kotlin Multiplatform libraries imported through Gradle are supported
 */
@Service(Service.Level.PROJECT)
@K1ModeProjectStructureApi
class LibraryInfoVariantsService(project: Project) : Disposable {

    private val storage = mutableMapOf<MavenGroupArtifactId, MutableSet<LibraryInfo>>()

    private val libraryInfoListener = object : LibraryInfoListener {
        override fun libraryInfosAdded(libraryInfos: Collection<LibraryInfo>) = useStorage {
            libraryInfos.forEach { libraryInfo ->
                val id = libraryInfo.mavenGroupArtifactId()
                if (id != null) {
                    storage.computeIfAbsent(id) {
                        sortedSetOf(compareBy { it.name }) // stable order
                    }.add(libraryInfo)
                }
            }
        }

        override fun libraryInfosRemoved(libraryInfos: Collection<LibraryInfo>) = useStorage {
            libraryInfos.forEach { libraryInfo ->
                storage[libraryInfo.mavenGroupArtifactId()]?.remove(libraryInfo)
            }
        }
    }

    init {
        project.messageBus.connect(this).subscribe(LibraryInfoListener.TOPIC, libraryInfoListener)
        libraryInfoListener.libraryInfosAdded(LibraryInfoCache.getInstance(project).values().flatten())
    }

    fun variants(libraryInfo: LibraryInfo): List<LibraryInfo> = useStorage {
        return storage[libraryInfo.mavenGroupArtifactId()].orEmpty().toList()
    }

    override fun dispose() = useStorage {
        storage.clear()
    }

    private inline fun <R> useStorage(block: () -> R) = synchronized(storage, block)

    /*
        Supported formats:

        [prefix:] <groupId>:<artifactId>:<variant>:<version>
        [prefix:] <groupId>:<artifactId>-<variant>:<version>
     */
    private fun LibraryInfo.mavenGroupArtifactId(): MavenGroupArtifactId? {

        if (bundledLibraryVariant(this) != null) {
            return "org.jetbrains.kotlin:kotlin-bundled"
        }

        val externalSource = library.externalSource
        if (externalSource?.id != "GRADLE") {
            return null
        }

        return extractArtifactIdWithoutVariant(library.name)
    }

    companion object {
        fun getInstance(project: Project): LibraryInfoVariantsService = project.service()

        enum class BundledLibraryVariant(val displayName: String, val wellKnownCoordinates: Set<String>) {
            Jvm(
                "jvm", setOf(
                    "org.jetbrains.kotlin:kotlin-test",
                    "org.jetbrains.kotlin:kotlin-test-junit",
                    "org.jetbrains.kotlin:kotlin-test-junit5",
                    "org.jetbrains.kotlin:kotlin-test-testng",
                )
            ),
            Js("js", setOf("org.jetbrains.kotlin:kotlin-test-js")),
            Native("native", emptySet()),
            Common(
                "common", setOf(
                    "org.jetbrains.kotlin:kotlin-test-common",
                    "org.jetbrains.kotlin:kotlin-test-annotations-common",
                    "org.jetbrains.kotlin:kotlin-test:annotationsCommonMain",
                    "org.jetbrains.kotlin:kotlin-test:assertionsCommonMain",
                    "org.jetbrains.kotlin:kotlin-stdlib:commonMain",
                )
            )
        }

        /**
         * Detects libraries that are bundled in the K/N distribution and their variants from maven.
         *
         * For now there are `stdlib` and `kotlin-test`
         */
        fun bundledLibraryVariant(libraryInfo: LibraryInfo): BundledLibraryVariant? {
            val name = libraryInfo.library.name.orEmpty().substringBeforeLast(':')
            return when {
                libraryInfo is NativeKlibLibraryInfo && libraryInfo.isStdlib -> BundledLibraryVariant.Native
                KotlinJvmStdlibDetectorFacility.isStdlib(libraryInfo.project, libraryInfo.library) -> BundledLibraryVariant.Jvm
                KotlinJavaScriptStdlibDetectorFacility.isStdlib(libraryInfo.project, libraryInfo.library) -> BundledLibraryVariant.Js
                else -> BundledLibraryVariant.entries.firstOrNull { bundledLibraryVariant ->
                    name in bundledLibraryVariant.wellKnownCoordinates
                }
            }
        }

        private val GRADLE_LIBRARY_NAME_REGEX: Regex =
            Regex("^(?<prefix>\\S+: )?(?<group>\\S+?):(?<artifactId>\\S+?):((?<variant>\\S+?):)?(?<version>\\S+)$")

        // group name constants are not used in the pattern because of the regex checker complains
        private const val GROUP = "group"
        private const val ARTIFACT_ID = "artifactId"
        private const val VARIANT = "variant"

        fun extractArtifactIdWithoutVariant(library: String?): String? {
            val match = GRADLE_LIBRARY_NAME_REGEX.matchEntire(library.orEmpty()) ?: return null
            val variant = match.groups[VARIANT]
            val groupId = match.groups[GROUP]?.value ?: return null
            val artifactId = match.groups[ARTIFACT_ID]?.value ?: return null

            val artifactIdWithoutVariant = if (variant != null) artifactId else artifactId.substringBeforeLast('-')
            return "$groupId:$artifactIdWithoutVariant"
        }

        fun extractVariantName(library: String?): String? {
            val match = GRADLE_LIBRARY_NAME_REGEX.matchEntire(library.orEmpty()) ?: return null
            val variant = match.groups[VARIANT]
            val artifactId = match.groups[ARTIFACT_ID]?.value ?: return null

            return variant?.value ?:  artifactId.substringAfterLast('-')
        }
    }
}

private typealias MavenGroupArtifactId = String