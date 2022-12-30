// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.test.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.ResolverForModuleComputationTracker
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.JvmLibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo

class ResolverTracker : ResolverForModuleComputationTracker, AutoCloseable {
    val moduleResolversComputed = mutableListOf<Module>()
    val sdkResolversComputed = mutableListOf<Sdk>()
    val librariesComputed = mutableListOf<LibraryEx>()

    override fun onResolverComputed(moduleInfo: ModuleInfo) {
        when (moduleInfo) {
            is JvmLibraryInfo -> librariesComputed.add(moduleInfo.library)
            is ModuleSourceInfo -> moduleResolversComputed.add(moduleInfo.module)
            is SdkInfo -> sdkResolversComputed.add(moduleInfo.sdk)
        }
    }

    fun clear() {
        librariesComputed.clear()
        moduleResolversComputed.clear()
        sdkResolversComputed.clear()
    }

    override fun close() {
        clear()
    }
}
