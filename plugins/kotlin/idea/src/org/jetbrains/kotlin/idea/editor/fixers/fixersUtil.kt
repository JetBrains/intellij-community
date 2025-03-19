// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.editor.fixers

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@get:ApiStatus.ScheduledForRemoval
@Deprecated(message = "use org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.range", level = DeprecationLevel.ERROR)
@get:Deprecated(message = "use org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.range", level = DeprecationLevel.ERROR)
val PsiElement.range: TextRange get() = textRange!!
@get:ApiStatus.ScheduledForRemoval
@Deprecated(message = "use org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.start", level = DeprecationLevel.ERROR)
@get:Deprecated(message = "use org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.start", level = DeprecationLevel.ERROR)
val TextRange.start: Int get() = startOffset
@get:ApiStatus.ScheduledForRemoval
@Deprecated(message = "use org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.end", level = DeprecationLevel.ERROR)
@get:Deprecated(message = "use org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.end", level = DeprecationLevel.ERROR)
val TextRange.end: Int get() = endOffset

@ApiStatus.ScheduledForRemoval
@Deprecated(message = "use org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.startLine", level = DeprecationLevel.ERROR)
fun PsiElement.startLine(doc: Document): Int = doc.getLineNumber(textRange!!.startOffset)
@ApiStatus.ScheduledForRemoval
@Deprecated(message = "use org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.endLine", level = DeprecationLevel.ERROR)
fun PsiElement.endLine(doc: Document): Int = doc.getLineNumber(textRange!!.endOffset)
@Deprecated(message = "use org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.isWithCaret", level = DeprecationLevel.ERROR)
fun PsiElement?.isWithCaret(caret: Int) = this?.textRange?.contains(caret) == true
