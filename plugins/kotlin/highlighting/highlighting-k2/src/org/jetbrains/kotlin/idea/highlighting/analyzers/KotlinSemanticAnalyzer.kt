// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.analyzers

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * A [KtVisitorVoid] which generates a semantic INFORMATION-level highlightings (e.g., for smartcasts) and adds them to the [holder]
 *
 * The analyzer must not be recursive.
 * It is the responsibility of the containing [com.intellij.codeInsight.daemon.impl.HighlightVisitor]
 * to call the visitor on all elements.
 *
 * @see org.jetbrains.kotlin.idea.highlighting.visitor.KotlinAbstractSemanticHighlightingVisitor
 */
internal abstract class KotlinSemanticAnalyzer(
    protected val holder: HighlightInfoHolder,

    /**
     * This is safe to store [KaSession] as its lifetime is limited to the corresponding [org.jetbrains.kotlin.analysis.api.analyze]
     * block inside [org.jetbrains.kotlin.idea.highlighting.visitor.KotlinAbstractSemanticHighlightingVisitor].
     *
     * But it must not be exposed or stored outside this visitor.
     */
    protected val session: KaSession,
) : KtVisitorVoid()