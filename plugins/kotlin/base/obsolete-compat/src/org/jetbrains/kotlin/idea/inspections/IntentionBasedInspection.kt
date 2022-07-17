// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor as _findExistingEditor

@Deprecated("Please use org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor instead")
fun PsiElement.findExistingEditor(): Editor? =
    _findExistingEditor()
