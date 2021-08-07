// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex;

import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;

public class KotlinIndexUtil {
    @NotNull
    public static <K, Psi extends PsiElement> StubIndexKey<K, Psi> createIndexKey(@NotNull Class<? extends StubIndexExtension<K, Psi>> indexClass) {
        return StubIndexKey.createIndexKey(indexClass.getCanonicalName());
    }

    private KotlinIndexUtil() {}
}
