// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.vfilefinder.KotlinStdlibIndex
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizard
import kotlin.io.path.name

abstract class AbstractK2JvmBasicCompletionStdlibDuplicationTest : AbstractK2JvmBasicCompletionTest() {

    override fun extraLookupElementCheck(lookupElement: LookupElement) {
        SerializabilityChecker.checkLookupElement(lookupElement, myFixture.project)
    }

    override fun setUp() {
        super.setUp()

        val libraryRoot = TestKotlinArtifacts.kotlinStdLibProjectWizardDefault

        assertEquals(
            "${
                KotlinStdlibIndex.KOTLIN_STDLIB_NAME.shortName().asString()
            }-${BuildSystemKotlinNewProjectWizard.DEFAULT_KOTLIN_VERSION}.jar",
            libraryRoot.name
        )

        ModuleRootModificationUtil.addModuleLibrary(
            /* module = */ module,
            /* classesRootUrl = */ VfsUtil.getUrlForLibraryRoot(libraryRoot),
        )
    }
}