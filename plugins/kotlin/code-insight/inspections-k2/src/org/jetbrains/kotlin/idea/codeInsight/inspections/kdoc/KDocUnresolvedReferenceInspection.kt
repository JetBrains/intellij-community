// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.kdoc

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection

/**
 * This is a proxy inspection for [org.jetbrains.kotlin.idea.codeInsight.inspections.kdoc.KDocUnresolvedLinkAnnotator].
 */
internal class KDocUnresolvedReferenceInspection : LocalInspectionTool(), ExternalAnnotatorBatchInspection {
    override fun getShortName(): String {
        return SHORT_NAME
    }

    companion object {
        const val SHORT_NAME: String = "KDocUnresolvedReference"
    }
}