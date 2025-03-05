// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.parcelize.ide.test

import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.idea.k2.quickfix.tests.AbstractK2QuickFixTest
import org.jetbrains.kotlin.parcelize.ParcelizeNames
import org.jetbrains.kotlin.parcelize.fir.FirParcelizeExtensionRegistrar

abstract class AbstractParcelizeK2QuickFixTest : AbstractK2QuickFixTest() {
    override fun setUp() {
        super.setUp()
        addParcelizeLibraries(module)
        if (!project.extensionArea.hasExtensionPoint(FirExtensionRegistrarAdapter.extensionPointName)) {
            FirExtensionRegistrarAdapter.registerExtensionPoint(project)
        }
        FirExtensionRegistrarAdapter.registerExtension(
            project,
            FirParcelizeExtensionRegistrar(ParcelizeNames.PARCELIZE_CLASS_FQ_NAMES)
        )
    }

    override fun tearDown() {
        runAll(
            {
                project.extensionArea
                    .getExtensionPoint(FirExtensionRegistrarAdapter.extensionPointName)
                    .unregisterExtension(FirParcelizeExtensionRegistrar::class.java)
            },
            { project.extensionArea.unregisterExtensionPoint(FirExtensionRegistrarAdapter.extensionPointName.name) },
            { removeParcelizeLibraries(module) },
            { super.tearDown() },
        )
    }
}