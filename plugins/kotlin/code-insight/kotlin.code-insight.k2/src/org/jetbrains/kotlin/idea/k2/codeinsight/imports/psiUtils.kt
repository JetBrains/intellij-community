// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.PsiCommentImpl

/**
 * We ignore references from such elements because resolving them leads to UAST resolution,
 * and that in turn leads to KT-68102 when import optimization is called after move refactoring or J2K.
 *
 * This happens mostly because [com.intellij.psi.PsiElement.references] implementation
 * in those cases leads to [com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry.getReferencesFromProviders],
 * and that might lead to UAST resolve.
 *
 * Ignoring references from such elements should be safe, because in Import Optimizer
 * we do not care about externally contributed references.
 */
internal val PsiElement.ignoreReferencesDuringImportOptimization: Boolean
    get() = this is ContributedReferenceHost ||
            this is PsiCommentImpl