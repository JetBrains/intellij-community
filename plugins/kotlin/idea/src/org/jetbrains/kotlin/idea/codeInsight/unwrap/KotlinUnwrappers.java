// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.*;

public class KotlinUnwrappers {
    private KotlinUnwrappers() {
    }

    public static class KotlinExpressionRemover extends KotlinRemover {
        public KotlinExpressionRemover(String key) {
            super(key);
        }

        @Override
        public boolean isApplicableTo(PsiElement e) {
            return e instanceof KtExpression && e.getParent() instanceof KtBlockExpression;
        }
    }

    public static class KotlinElseUnwrapper extends KotlinComponentUnwrapper {
        public KotlinElseUnwrapper(String key) {
            super(key);
        }

        @Override
        protected KtExpression getExpressionToUnwrap(@NotNull KtElement target) {
            return target instanceof KtIfExpression ? ((KtIfExpression) target).getElse() : null;
        }
    }

    public static class KotlinThenUnwrapper extends KotlinComponentUnwrapper {
        public KotlinThenUnwrapper(String key) {
            super(key);
        }

        @Override
        protected KtExpression getExpressionToUnwrap(@NotNull KtElement target) {
            return target instanceof KtIfExpression ? ((KtIfExpression) target).getThen() : null;
        }
    }

    public static class KotlinElseRemover extends KotlinUnwrapRemoveBase {
        public KotlinElseRemover(String key) {
            super(key);
        }

        @Override
        public boolean isApplicableTo(PsiElement e) {
            return e instanceof KtIfExpression
                   && ((KtIfExpression) e).getCondition() != null
                   && ((KtIfExpression) e).getThen() != null
                   && ((KtIfExpression) e).getElse() != null;
        }

        @Override
        protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
            KtIfExpression ifExpr = (KtIfExpression) element;
            context.replace(ifExpr, new KtPsiFactory(ifExpr.getProject()).createIf(ifExpr.getCondition(), ifExpr.getThen(), null));
        }
    }

    public static class KotlinLoopUnwrapper extends KotlinComponentUnwrapper {
        public KotlinLoopUnwrapper(String key) {
            super(key);
        }

        @Override
        @Nullable
        protected KtExpression getExpressionToUnwrap(@NotNull KtElement target) {
            return target instanceof KtLoopExpression ? ((KtLoopExpression) target).getBody() : null;
        }
    }

    public static class KotlinTryUnwrapper extends KotlinComponentUnwrapper {
        public KotlinTryUnwrapper(String key) {
            super(key);
        }

        @Override
        @Nullable
        protected KtExpression getExpressionToUnwrap(@NotNull KtElement target) {
            return target instanceof KtTryExpression ? ((KtTryExpression) target).getTryBlock() : null;
        }
    }

    public static class KotlinCatchUnwrapper extends KotlinComponentUnwrapper {
        public KotlinCatchUnwrapper(String key) {
            super(key);
        }

        @NotNull
        @Override
        protected KtElement getEnclosingElement(@NotNull KtElement element) {
            return (KtElement)element.getParent();
        }

        @Override
        protected KtExpression getExpressionToUnwrap(@NotNull KtElement target) {
            return target instanceof KtCatchClause ? ((KtCatchClause) target).getCatchBody() : null;
        }
    }

    public static class KotlinCatchRemover extends KotlinRemover {
        public KotlinCatchRemover(String key) {
            super(key);
        }

        @Override
        public boolean isApplicableTo(PsiElement e) {
            return e instanceof KtCatchClause;
        }
    }

    public static class KotlinFinallyUnwrapper extends KotlinComponentUnwrapper {
        public KotlinFinallyUnwrapper(String key) {
            super(key);
        }

        @Override
        public boolean isApplicableTo(PsiElement e) {
            return super.isApplicableTo(e) && getEnclosingElement((KtElement)e).getParent() instanceof KtBlockExpression;
        }

        @NotNull
        @Override
        protected KtElement getEnclosingElement(@NotNull KtElement element) {
            return (KtElement)element.getParent();
        }

        @Override
        protected KtExpression getExpressionToUnwrap(@NotNull KtElement target) {
            return target instanceof KtFinallySection ? ((KtFinallySection) target).getFinalExpression() : null;
        }
    }

    public static class KotlinFinallyRemover extends KotlinRemover {
        public KotlinFinallyRemover(String key) {
            super(key);
        }

        @Override
        public boolean isApplicableTo(PsiElement e) {
            return e instanceof KtFinallySection;
        }
    }
}
