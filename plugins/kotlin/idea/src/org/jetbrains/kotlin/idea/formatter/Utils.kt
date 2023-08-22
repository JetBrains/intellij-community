// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.formatter

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument as _commitAndUnblockDocument

@Deprecated(
    "Please use `org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument` instead",
    ReplaceWith("commitAndUnblockDocument()", "org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument"),
    level = DeprecationLevel.ERROR
)
fun PsiFile.commitAndUnblockDocument(): Boolean = _commitAndUnblockDocument()