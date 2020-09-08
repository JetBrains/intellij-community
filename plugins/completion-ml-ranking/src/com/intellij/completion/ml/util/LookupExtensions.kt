/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.completion.ml.util

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.lang.Language
import com.intellij.psi.util.PsiUtilCore

/**
 * @return a number of requests in the current completion session
 */
fun LookupImpl.queryLength(): Int {
    val lookupOriginalStart = this.lookupOriginalStart
    val caretOffset = this.editor.caretModel.offset
    return if (lookupOriginalStart < 0) 0 else caretOffset - lookupOriginalStart + 1
}

/**
 * @return a prefix in the current completion session
 */
fun Lookup.prefix(): String {
    val text = editor.document.text
    val offset = editor.caretModel.offset
    var startOffset = offset
    for (i in offset - 1 downTo 0) {
        if (!text[i].isJavaIdentifierPart()) break
        startOffset = i
    }
    return text.substring(startOffset, offset)
}

fun LookupImpl.language(): Language? {
    val file = psiFile ?: return null
    val offset = editor.caretModel.offset
    return  PsiUtilCore.getLanguageAtOffset(file, offset)
}