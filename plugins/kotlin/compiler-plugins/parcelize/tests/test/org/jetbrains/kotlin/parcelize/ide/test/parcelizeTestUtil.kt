// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.parcelize.ide.test

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderRootType
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addRoot
import java.io.File

fun addParcelizeLibraries(module: Module) {
    ConfigLibraryUtil.addLibrary(module, "androidJar") {
        addRoot(File(PathManager.getCommunityHomePath(), "android/android/testData/android.jar"), OrderRootType.CLASSES)
    }
    ConfigLibraryUtil.addLibrary(module, "parcelizeRuntime") {
        addRoot(TestKotlinArtifacts.parcelizeRuntime, OrderRootType.CLASSES)
    }
    ConfigLibraryUtil.addLibrary(module, "androidExtensionsRuntime") {
        addRoot(TestKotlinArtifacts.androidExtensionsRuntime, OrderRootType.CLASSES)
    }

}

fun removeParcelizeLibraries(module: Module) {
    ConfigLibraryUtil.removeLibrary(module, "androidJar")
    ConfigLibraryUtil.removeLibrary(module, "parcelizeRuntime")
    ConfigLibraryUtil.removeLibrary(module, "androidExtensionsRuntime")
}