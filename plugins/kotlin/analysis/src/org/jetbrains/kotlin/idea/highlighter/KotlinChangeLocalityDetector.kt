// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.ChangeLocalityDetector
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.caches.trackers.PureKotlinCodeBlockModificationListener.Companion.getInsideCodeBlockModificationDirtyScope

class KotlinChangeLocalityDetector : ChangeLocalityDetector {
    override fun getChangeHighlightingDirtyScopeFor(element: PsiElement): PsiElement? {
        // in some cases it returns a bit wider scope for the element as it is not possible to track changes here
        // e.g.: delete a space in expression `foo( )` results to entire expression `foo()`
        return getInsideCodeBlockModificationDirtyScope(element)
    }
}
