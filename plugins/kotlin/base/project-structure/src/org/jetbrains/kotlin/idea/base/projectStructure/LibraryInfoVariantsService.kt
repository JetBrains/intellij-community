// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo

/**
 * Stores mappings from [LibraryInfo] to all its variants. "Library variant" here means an imported library
 * which was published from the same Multiplatform module (Gradle subproject).
 *
 * For instance, a typical iOS/JVM/Linux-library would have the following variants:
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
 * Only Kotlin Multiplatform libraries imported through Gradle is supported
 */
@Service(Service.Level.PROJECT)
class LibraryInfoVariantsService(project: Project): Disposable {

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

        <groupId>:<artifactId>:<variant>:<version>
        <groupId>:<artifactId>-<variant>:<version>
     */
    private fun LibraryInfo.mavenGroupArtifactId(): MavenGroupArtifactId? {

        val externalSource = library.externalSource
        if (externalSource?.id != "GRADLE") {
            return null
        }

        val split = library.name.orEmpty().split(":")
        if (split.size != 3 && split.size != 4) {
            return null
        }

        return when (split.size) {
            3 -> split[0] + ":" + split[1].substringBeforeLast('-')
            4 -> split[0] + ":" + split[1]
            else -> null
        }
    }

    companion object {
        fun getInstance(project: Project): LibraryInfoVariantsService = project.service()
    }
}

private typealias MavenGroupArtifactId = String