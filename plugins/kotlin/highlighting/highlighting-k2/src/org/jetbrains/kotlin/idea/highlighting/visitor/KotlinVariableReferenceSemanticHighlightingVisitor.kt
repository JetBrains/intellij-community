// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.visitor

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.highlighting.analyzers.KotlinSemanticAnalyzer
import org.jetbrains.kotlin.idea.highlighting.analyzers.KotlinVariableReferenceSemanticAnalyzer

internal class KotlinVariableReferenceSemanticHighlightingVisitor : KotlinAbstractSemanticHighlightingVisitor() {
    override fun clone(): KotlinVariableReferenceSemanticHighlightingVisitor = KotlinVariableReferenceSemanticHighlightingVisitor()
    override fun createSemanticAnalyzer(holder: HighlightInfoHolder, session: KaSession): KotlinSemanticAnalyzer {
        return KotlinVariableReferenceSemanticAnalyzer(holder, session)
    }
}
