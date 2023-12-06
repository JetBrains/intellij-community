// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.modificationEvents

import com.intellij.openapi.project.Project

abstract class AbstractKotlinGlobalModificationEventTest<TRACKER : GlobalModificationEventTracker> : AbstractKotlinModificationEventTest<TRACKER>() {
    abstract fun constructTracker(): TRACKER

    /**
     * Creates and initializes a tracker to track global modification events. The tracker will be disposed with the test root disposable and
     * does not need to be disposed manually.
     */
    fun createTracker(): TRACKER = constructTracker().apply { initialize(testRootDisposable) }
}

abstract class GlobalModificationEventTracker(project: Project, eventKind: String) : ModificationEventTracker(project, eventKind)
