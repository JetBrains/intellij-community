// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Experimental
public interface ChangeSignatureUsageProvider {
    @Nullable UsageInfo createUsageInfo(@NotNull ChangeInfo changeInfo,
                                        @NotNull PsiReference reference,
                                        @NotNull PsiElement method,
                                        boolean modifyArgs,
                                        boolean modifyExceptions);

    @Nullable UsageInfo createOverrideUsageInfo(@NotNull ChangeInfo changeInfo,
                                                @NotNull PsiElement overrider,
                                                @NotNull PsiElement baseMethod,
                                                boolean isOriginalOverrider,
                                                boolean modifyArgs,
                                                boolean modifyExceptions,
                                                List<? super UsageInfo> result);
}
