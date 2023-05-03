// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.highlighting.visitor

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class AbstractAnnotationHolderHighlightingVisitor(holder: HighlightInfoHolder): AbstractHighlightingVisitor(holder) {
}