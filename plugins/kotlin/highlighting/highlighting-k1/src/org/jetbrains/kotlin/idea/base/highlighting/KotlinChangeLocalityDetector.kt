// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.codeInsight.daemon.ChangeLocalityDetector
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.caches.trackers.PureKotlinCodeBlockModificationListener

@ApiStatus.Internal
class KotlinChangeLocalityDetector : ChangeLocalityDetector {
    override fun getChangeHighlightingDirtyScopeFor(element: PsiElement): PsiElement? {
        // in some cases it returns a bit wider scope for the element as it is not possible to track changes here
        // e.g.: delete a space in expression `foo( )` results to entire expression `foo()`
        return PureKotlinCodeBlockModificationListener.getInsideCodeBlockModificationDirtyScope(element)
    }
}