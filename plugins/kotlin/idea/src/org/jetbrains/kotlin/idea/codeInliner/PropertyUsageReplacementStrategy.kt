// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInliner

import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.resolve.references.ReferenceAccess

class PropertyUsageReplacementStrategy(readReplacement: CodeToInline?, writeReplacement: CodeToInline?) : UsageReplacementStrategy {
    private val readReplacementStrategy = readReplacement?.let {
        CallableUsageReplacementStrategy(it, inlineSetter = false)
    }

    private val writeReplacementStrategy = writeReplacement?.let {
        CallableUsageReplacementStrategy(it, inlineSetter = true)
    }

    override fun createReplacer(usage: KtReferenceExpression): (() -> KtElement?)? {
        return when (usage.readWriteAccess(useResolveForReadWrite = true)) {
            ReferenceAccess.READ -> readReplacementStrategy?.createReplacer(usage)
            ReferenceAccess.WRITE -> writeReplacementStrategy?.createReplacer(usage)
            ReferenceAccess.READ_WRITE -> null
        }
    }
}
