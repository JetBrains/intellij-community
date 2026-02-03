// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.quickfix;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.descriptors.CallableDescriptor;
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor;
import org.jetbrains.kotlin.idea.caches.resolve.ResolutionUtils;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtNamedDeclaration;
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode;
import org.jetbrains.kotlin.types.DeferredType;
import org.jetbrains.kotlin.types.KotlinType;

public final class QuickFixUtil {
    private QuickFixUtil() {
    }

    /**
     * @deprecated Avoid using unsafe resolution methods and unwrapping 'DeferredType's.
     */
    @Deprecated
    public static @Nullable KotlinType getDeclarationReturnType(KtNamedDeclaration declaration) {
        PsiFile file = declaration.getContainingFile();
        if (!(file instanceof KtFile)) return null;
        DeclarationDescriptor descriptor = ResolutionUtils.unsafeResolveToDescriptor(declaration, BodyResolveMode.FULL);
        if (!(descriptor instanceof CallableDescriptor)) return null;
        KotlinType type = ((CallableDescriptor) descriptor).getReturnType();
        if (type instanceof DeferredType) {
            type = ((DeferredType) type).getDelegate();
        }
        return type;
    }
}
