// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import kotlinx.coroutines.asContextElement
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.CoroutineContext

/**
 * Needed because [FileNavigatorImpl] reads the editor from the ambient data context, and a target which navigates by itself
 * has no options to be told anything: taking that editor away is the only way to enforce `RequestedEditor.None` for it.
 *
 * Bright future: goes away once `OpenFileDescriptor` and the `CompositePsiElement` / `PsiFileImpl` report a navigation request;
 * the editor then comes from the options alone, the ambient read is obsolete.
 */
private val contextEditorSuppressed: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

@get:Internal
val isContextEditorSuppressed: Boolean
  get() = contextEditorSuppressed.get()

/**
 * To suppress the ambient [OpenFileDescriptor.NAVIGATE_IN_EDITOR] editor.
 *
 * This is how `RequestedEditor.None` is enforced, state the intent there rather than installing this element by hand.
 */
@Internal
fun editorSuppressionCoroutineContext(): CoroutineContext = contextEditorSuppressed.asContextElement(true)
