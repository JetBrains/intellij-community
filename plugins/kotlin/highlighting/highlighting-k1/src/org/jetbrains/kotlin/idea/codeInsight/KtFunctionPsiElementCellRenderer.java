// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight;

import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor;
import org.jetbrains.kotlin.idea.caches.resolve.ResolutionUtils;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.renderer.DescriptorRenderer;
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode;

public class KtFunctionPsiElementCellRenderer extends PsiTargetPresentationRenderer<PsiElement> {
    @Override
    public @NotNull String getElementText(@NotNull PsiElement element) {
        if (element instanceof KtNamedFunction function) {
          DeclarationDescriptor descriptor = ResolutionUtils.unsafeResolveToDescriptor(function, BodyResolveMode.PARTIAL);
            return DescriptorRenderer.SHORT_NAMES_IN_TYPES.render(descriptor); //NON-NLS
        }
        return super.getElementText(element);
    }

    @Override
    public @NotNull TargetPresentation getPresentation(@NotNull PsiElement element) {
        return TargetPresentation.builder(getElementText(element))
                .containerText(SymbolPresentationUtil.getSymbolContainerText(element))
                .icon(getIcon(element))
                .presentation();
    }
}
