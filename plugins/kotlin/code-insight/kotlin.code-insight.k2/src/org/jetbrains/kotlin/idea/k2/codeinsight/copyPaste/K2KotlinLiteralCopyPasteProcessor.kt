// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.copyPaste

import org.jetbrains.kotlin.idea.editor.KotlinLiteralCopyPasteProcessor

internal class K2KotlinLiteralCopyPasteProcessor : KotlinLiteralCopyPasteProcessor() {
    override val singleQuotedEntryProcessingMode: EntryProcessingMode
        get() = EntryProcessingMode.ESCAPED
}
