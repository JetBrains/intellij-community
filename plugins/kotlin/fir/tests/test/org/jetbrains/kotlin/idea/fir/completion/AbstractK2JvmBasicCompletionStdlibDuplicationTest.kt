// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.completion

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.kotlin.idea.base.plugin.artifacts.downloadOrReportUnavailability
import org.jetbrains.kotlin.idea.vfilefinder.KotlinStdlibIndex
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizard

abstract class AbstractK2JvmBasicCompletionStdlibDuplicationTest : AbstractK2JvmBasicCompletionTest() {

    override fun setUp() {
        super.setUp()

        val libraryRoot = downloadOrReportUnavailability(
            artifactId = KotlinStdlibIndex.KOTLIN_STDLIB_NAME.shortName().asString(),
            version = BuildSystemKotlinNewProjectWizard.DEFAULT_KOTLIN_VERSION,
        )

        ModuleRootModificationUtil.addModuleLibrary(
            /* module = */ module,
            /* classesRootUrl = */ VfsUtil.getUrlForLibraryRoot(libraryRoot),
        )
    }
}