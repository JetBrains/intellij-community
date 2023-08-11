// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.analysis.providers

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.base.fir.analysisApiProviders.FirIdeOutOfBlockModificationService

internal fun Module.publishOutOfBlockModification() {
    runWriteAction {
        FirIdeOutOfBlockModificationService.getInstance(project).publishModuleOutOfBlockModification(this)
    }
}
