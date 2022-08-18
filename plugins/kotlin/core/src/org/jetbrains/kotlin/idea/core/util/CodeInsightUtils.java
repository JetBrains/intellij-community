// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.util;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiUtils;

public class CodeInsightUtils {
    /**
     * @deprecated Use org.jetbrains.kotlin.idea.base.psi.CodeInsightUtils.getTopmostElementAtOffset instead
     */
    @NotNull
    @Deprecated
    public static PsiElement getTopmostElementAtOffset(@NotNull PsiElement element, int offset) {
        return KotlinPsiUtils.getTopmostElementAtOffset(element, offset);
    }
}
