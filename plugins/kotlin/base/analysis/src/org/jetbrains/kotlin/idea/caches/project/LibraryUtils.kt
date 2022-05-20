// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LibraryUtils")
package org.jetbrains.kotlin.idea.caches.project

import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo

fun ModuleInfo.findSdkAcrossDependencies(): SdkInfo? {
    val project = (this as? IdeaModuleInfo)?.project ?: return null

    return SdkInfoCache.getInstance(project).findOrGetCachedSdk(this)
}

fun IdeaModuleInfo.findJvmStdlibAcrossDependencies(): LibraryInfo? {
    val project = project ?: return null

    return KotlinStdlibCache.getInstance(project).findStdlibInModuleDependencies(this)
}