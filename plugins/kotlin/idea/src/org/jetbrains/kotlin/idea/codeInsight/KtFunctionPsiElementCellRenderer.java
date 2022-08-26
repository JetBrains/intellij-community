// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight;

import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.psi.PsiElement;
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor;
import org.jetbrains.kotlin.idea.caches.resolve.ResolutionUtils;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.renderer.DescriptorRenderer;
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode;

public class KtFunctionPsiElementCellRenderer extends DefaultPsiElementCellRenderer {
    @Override
    public String getElementText(PsiElement element) {
        if (element instanceof KtNamedFunction) {
            KtNamedFunction function = (KtNamedFunction) element;
            DeclarationDescriptor descriptor = ResolutionUtils.unsafeResolveToDescriptor(function, BodyResolveMode.PARTIAL);
            return DescriptorRenderer.SHORT_NAMES_IN_TYPES.render(descriptor);
        }
        return super.getElementText(element);
    }
}
