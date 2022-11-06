// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.gradle.workspace.checkWorkspaceModel
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase

abstract class AbstractWorkspaceModelPrintingGradleImportingTest : MultiplePluginVersionGradleImportingTestCase() {
    override fun testDataDirName(): String = "workspaceModel"

    protected fun doTest() {
        configureByFiles()
        importProject()
        checkWorkspaceModel(myProject, testDataDirectory())
    }
}
