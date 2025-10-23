// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k1.codeinsight.copyPaste

import org.jetbrains.kotlin.idea.editor.KotlinLiteralCopyPasteProcessor

internal class K1KotlinLiteralCopyPasteProcessor : KotlinLiteralCopyPasteProcessor() {
    override val singleQuotedEntryProcessingMode: EntryProcessingMode
        get() = EntryProcessingMode.NOT_ESCAPED
}
