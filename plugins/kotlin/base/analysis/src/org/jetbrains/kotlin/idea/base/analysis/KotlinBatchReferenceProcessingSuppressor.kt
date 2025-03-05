// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis

import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiFileEx
import org.jetbrains.kotlin.analysis.api.platform.resolution.KaResolutionActivityTracker

internal class KotlinBatchReferenceProcessingSuppressor : PsiFileEx.BatchReferenceProcessingSuppressor {
    override fun isSuppressed(file: PsiFile): Boolean {
        return KaResolutionActivityTracker.getInstance()?.isKotlinResolutionActive == true
    }
}
