// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.util.PathUtil
import com.intellij.util.messages.Topic
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import org.jetbrains.kotlin.idea.base.util.caching.SynchronizedFineGrainedEntityCache
import org.jetbrains.kotlin.idea.base.platforms.LibraryEffectiveKindProvider
import org.jetbrains.kotlin.idea.base.platforms.isKlibLibraryRootForPlatform
import org.jetbrains.kotlin.idea.base.platforms.platform
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.util.caching.LibraryEntityChangeListener
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.impl.CommonIdePlatformKind
import org.jetbrains.kotlin.platform.impl.JsIdePlatformKind
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.kotlin.platform.impl.NativeIdePlatformKind
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.utils.addToStdlib.flattenTo

class LibraryInfoCache(project: Project): SynchronizedFineGrainedEntityCache<Library, List<LibraryInfo>>(project, cleanOnLowMemory = true) {
    override fun subscribe() {
        val busConnection = project.messageBus.connect(this)
        WorkspaceModelTopics.getInstance(project).subscribeImmediately(busConnection, ModelChangeListener(project))
    }

    override fun checkKeyValidity(key: Library) {
        key.checkValidity()
    }

    override fun calculate(key: Library): List<LibraryInfo> {
        val libraryInfos = when (val platformKind = getPlatform(key).idePlatformKind) {
            is JvmIdePlatformKind -> listOf(JvmLibraryInfo(project, key))
            is CommonIdePlatformKind -> createLibraryInfos(key, platformKind, ::CommonKlibLibraryInfo, ::CommonMetadataLibraryInfo)
            is JsIdePlatformKind -> createLibraryInfos(key, platformKind, ::JsKlibLibraryInfo, ::JsMetadataLibraryInfo)
            is NativeIdePlatformKind -> createLibraryInfos(key, platformKind, ::NativeKlibLibraryInfo, null)
            else -> error("Unexpected platform kind: $platformKind")
        }
        return libraryInfos
    }

    override fun postProcessNewValue(key: Library, value: List<LibraryInfo>) {
        project.messageBus.syncPublisher(LibraryInfoListener.TOPIC).libraryInfosAdded(value)
    }

    private fun createLibraryInfos(
        library: Library,
        platformKind: IdePlatformKind,
        klibLibraryInfoFactory: (Project, Library, String) -> LibraryInfo,
        metadataLibraryInfoFactory: ((Project, Library) -> LibraryInfo)?
    ): List<LibraryInfo> {
        val defaultPlatform = platformKind.defaultPlatform
        val klibFiles = library.getFiles(OrderRootType.CLASSES).filter { it.isKlibLibraryRootForPlatform(defaultPlatform) }

        return if (klibFiles.isNotEmpty()) {
            ArrayList<LibraryInfo>(klibFiles.size).apply {
                for (file in klibFiles) {
                    val path = PathUtil.getLocalPath(file) ?: continue
                    add(klibLibraryInfoFactory(project, library, path))
                }
            }
        } else if (metadataLibraryInfoFactory != null) {
            listOf(metadataLibraryInfoFactory(project, library))
        } else {
            emptyList()
        }
    }

    private fun getPlatform(library: Library): TargetPlatform =
        if (library is LibraryEx && !library.isDisposed) {
            project.service<LibraryEffectiveKindProvider>().getEffectiveKind(library).platform
        } else {
            JvmPlatforms.defaultJvmPlatform
        }

    internal class ModelChangeListener(project: Project) : LibraryEntityChangeListener(project, afterChangeApplied = false) {

        override fun entitiesChanged(outdated: List<Library>) {
            val libraryInfoCache = getInstance(project)
            val droppedLibraryInfos = libraryInfoCache.invalidateKeysAndGetOutdatedValues(outdated).flattenTo(hashSetOf())

            if (droppedLibraryInfos.isNotEmpty()) {
                project.messageBus.syncPublisher(LibraryInfoListener.TOPIC).libraryInfosRemoved(droppedLibraryInfos)
            }
        }
    }

    companion object {
        fun getInstance(project: Project): LibraryInfoCache = project.service()
    }
}

interface LibraryInfoListener {

    fun libraryInfosRemoved(libraryInfos: Collection<LibraryInfo>)

    fun libraryInfosAdded(libraryInfos: Collection<LibraryInfo>) {}

    companion object {
        @JvmStatic
        @Topic.ProjectLevel
        val TOPIC = Topic.create("library info listener", LibraryInfoListener::class.java)
    }
}
