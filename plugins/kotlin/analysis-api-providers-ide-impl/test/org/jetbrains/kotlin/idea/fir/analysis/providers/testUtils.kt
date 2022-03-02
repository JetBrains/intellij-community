// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.analysis.providers

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.analysis.providers.ide.trackers.KotlinFirModificationTrackerService

internal fun Module.incModificationTracker() {
    project.service<KotlinFirModificationTrackerService>().increaseModificationCountForModule(this)
}