// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.descriptors.CallableDescriptor;
import org.jetbrains.kotlin.psi.*;
import org.jetbrains.kotlin.types.KotlinType;
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker;

final class QuickFixBranchUtil {
    private QuickFixBranchUtil() {
    }

    static @Nullable KotlinType findLowerBoundOfOverriddenCallablesReturnTypes(@NotNull CallableDescriptor descriptor) {
        KotlinType matchingReturnType = null;
        for (CallableDescriptor overriddenDescriptor : descriptor.getOverriddenDescriptors()) {
            KotlinType overriddenReturnType = overriddenDescriptor.getReturnType();
            if (overriddenReturnType == null) {
                return null;
            }
            if (matchingReturnType == null || KotlinTypeChecker.DEFAULT.isSubtypeOf(overriddenReturnType, matchingReturnType)) {
                matchingReturnType = overriddenReturnType;
            }
            else if (!KotlinTypeChecker.DEFAULT.isSubtypeOf(matchingReturnType, overriddenReturnType)) {
                return null;
            }
        }
        return matchingReturnType;
    }

    private static boolean equalOrLastInBlock(KtExpression block, KtExpression expression) {
        if (block == expression) return true;
        return block instanceof KtBlockExpression && expression.getParent() == block &&
               PsiTreeUtil.getNextSiblingOfType(expression, KtExpression.class) == null;
    }

    static @Nullable KtIfExpression getParentIfForBranch(@Nullable KtExpression expression) {
        KtIfExpression ifExpression = PsiTreeUtil.getParentOfType(expression, KtIfExpression.class, true);
        if (ifExpression == null) return null;
        if (equalOrLastInBlock(ifExpression.getThen(), expression)
            || equalOrLastInBlock(ifExpression.getElse(), expression)) {
            return ifExpression;
        }
        return null;
    }

    private static @Nullable KtWhenExpression getParentWhenForBranch(@Nullable KtExpression expression) {
        KtWhenEntry whenEntry = PsiTreeUtil.getParentOfType(expression, KtWhenEntry.class, true);
        if (whenEntry == null) return null;
        KtExpression whenEntryExpression = whenEntry.getExpression();
        if (whenEntryExpression == null) return null;
        if (!equalOrLastInBlock(whenEntryExpression, expression)) return null;
        return PsiTreeUtil.getParentOfType(whenEntry, KtWhenExpression.class, true);
    }

    private static @Nullable KtExpression getParentForBranch(@Nullable KtExpression expression) {
        KtExpression parent = getParentIfForBranch(expression);
        if (parent != null) return parent;
        return getParentWhenForBranch(expression);
    }

    // Returns true iff parent's value always or sometimes is evaluable to child's value, e.g.
    // parent = (x), child = x;
    // parent = if (...) x else y, child = x;
    // parent = y.x, child = x
    static boolean canEvaluateTo(KtExpression parent, KtExpression child) {
        if (parent == null || child == null) {
            return false;
        }
        while (parent != child) {
            PsiElement childParent = child.getParent();
            if (childParent instanceof KtParenthesizedExpression) {
                child = (KtExpression) childParent;
                continue;
            }
            if (childParent instanceof KtDotQualifiedExpression &&
                (child instanceof KtCallExpression || child instanceof KtDotQualifiedExpression)) {
                child = (KtExpression) childParent;
                continue;
            }
            child = getParentForBranch(child);
            if (child == null) return false;
        }
        return true;
    }

    static boolean canFunctionOrGetterReturnExpression(@NotNull KtDeclaration functionOrGetter, @NotNull KtExpression expression) {
        if (functionOrGetter instanceof KtFunctionLiteral) {
            KtBlockExpression functionLiteralBody = ((KtFunctionLiteral) functionOrGetter).getBodyExpression();
            PsiElement returnedElement = null;
            if (functionLiteralBody != null) {
                PsiElement[] children = functionLiteralBody.getChildren();
                int length = children.length;
                if (length > 0) {
                    returnedElement = children[length - 1];
                }
            }
            return returnedElement instanceof KtExpression && canEvaluateTo((KtExpression) returnedElement, expression);
        }
        else {
            if (functionOrGetter instanceof KtDeclarationWithInitializer && canEvaluateTo(((KtDeclarationWithInitializer) functionOrGetter).getInitializer(), expression)) {
                return true;
            }
            KtReturnExpression returnExpression = PsiTreeUtil.getParentOfType(expression, KtReturnExpression.class);
            return returnExpression != null && canEvaluateTo(returnExpression.getReturnedExpression(), expression);
        }
    }
}
