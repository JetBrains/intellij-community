// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.hierarchy.calls;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor;
import org.jetbrains.kotlin.descriptors.FunctionDescriptor;
import org.jetbrains.kotlin.descriptors.PropertyDescriptor;
import org.jetbrains.kotlin.psi.*;
import org.jetbrains.kotlin.resolve.BindingContext;
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils;

public abstract class CalleeReferenceVisitorBase extends KtTreeVisitorVoid {
    private final BindingContext bindingContext;
    private final boolean deepTraversal;

    protected CalleeReferenceVisitorBase(BindingContext bindingContext, boolean deepTraversal) {
        this.bindingContext = bindingContext;
        this.deepTraversal = deepTraversal;
    }

    protected abstract void processDeclaration(@NotNull KtSimpleNameExpression reference, @NotNull PsiElement declaration);

    @Override
    public void visitKtElement(@NotNull KtElement element) {
        if (deepTraversal || !(element instanceof KtClassOrObject || element instanceof KtNamedFunction)) {
            super.visitKtElement(element);
        }
    }

    @Override
    public void visitSimpleNameExpression(@NotNull KtSimpleNameExpression expression) {
        DeclarationDescriptor descriptor = bindingContext.get(BindingContext.REFERENCE_TARGET, expression);
        if (descriptor == null) return;

        PsiElement declaration = DescriptorToSourceUtils.descriptorToDeclaration(descriptor);
        if (declaration == null) return;

        if (isProperty(descriptor, declaration) || isCallable(descriptor, declaration, expression)) {
            processDeclaration(expression, declaration);
        }
    }

    // Accept callees of JetCallElement which refer to Kotlin function, Kotlin class or Java method
    private static boolean isCallable(DeclarationDescriptor descriptor, PsiElement declaration, KtSimpleNameExpression reference) {
        KtCallElement callElement = PsiTreeUtil.getParentOfType(reference, KtCallElement.class);
        if (callElement == null || !PsiTreeUtil.isAncestor(callElement.getCalleeExpression(), reference, false)) return false;

        return descriptor instanceof FunctionDescriptor
                 && (declaration instanceof KtClassOrObject
                     || declaration instanceof KtNamedFunction
                     || declaration instanceof PsiMethod);
    }

    // Accept only properties (not local variables or references to Java fields)
    private static boolean isProperty(DeclarationDescriptor descriptor, PsiElement declaration) {
        return descriptor instanceof PropertyDescriptor && declaration instanceof KtProperty;
    }
}
