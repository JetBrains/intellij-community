// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KlibCompatibilityInfoUtils")

package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.loader.KlibLoader
import org.jetbrains.kotlin.library.loader.reportLoadingProblemsIfAny
import org.jetbrains.kotlin.library.metadata.isCInteropLibrary
import org.jetbrains.kotlin.library.metadata.isCommonizedCInteropLibrary
import org.jetbrains.kotlin.library.metadataVersion
import org.jetbrains.kotlin.library.uniqueName
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import org.jetbrains.kotlin.platform.TargetPlatform

@K1ModeProjectStructureApi
abstract class AbstractKlibLibraryInfo internal constructor(project: Project, library: LibraryEx, val libraryRoot: String) :
    LibraryInfo(project, library) {
    val resolvedKotlinLibrary: KotlinLibrary? = KlibLoader {
        libraryPaths(libraryRoot)
    }.load().apply {
        reportLoadingProblemsIfAny { _, message -> LOG.warn(message) }
    }.librariesStdlibFirst.singleOrNull()

    val compatibilityInfo: KlibCompatibilityInfo by lazy { resolvedKotlinLibrary.compatibilityInfo }

    final override fun getLibraryRoots(): List<String> = listOf(libraryRoot)

    abstract override val platform: TargetPlatform // must override

    val uniqueName: String? by lazy { resolvedKotlinLibrary.safeRead(null) { uniqueName } }

    val isInterop: Boolean by lazy {
        resolvedKotlinLibrary.safeRead(false) { isCInteropLibrary() } ||
                resolvedKotlinLibrary.safeRead(false) { isCommonizedCInteropLibrary() }
    }

    companion object {
        private val LOG = Logger.getInstance(AbstractKlibLibraryInfo::class.java)
    }
}