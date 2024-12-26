// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.liveTemplates;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlighter;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.*;

public abstract class KotlinTemplateContextType extends TemplateContextType {
    private KotlinTemplateContextType(@NotNull @NlsContexts.Label String presentableName) {
        super(presentableName);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isInContext(@NotNull PsiFile file, int offset) {
        if (!PsiUtilCore.getLanguageAtOffset(file, offset).isKindOf(KotlinLanguage.INSTANCE)) {
            return false;
        }

        PsiElement element = file.findElementAt(offset);
        if (element == null) {
            element = file.findElementAt(offset - 1);
        }

        if (element instanceof PsiWhiteSpace) {
            return false;
        } else if (PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null) {
            return isCommentInContext();
        } else if (PsiTreeUtil.getParentOfType(element, KtPackageDirective.class) != null
                   || PsiTreeUtil.getParentOfType(element, KtImportDirective.class) != null) {
            return false;
        } else if (element instanceof LeafPsiElement) {
            IElementType elementType = ((LeafPsiElement) element).getElementType();
            if (elementType == KtTokens.IDENTIFIER) {
                PsiElement parent = element.getParent();
                if (parent instanceof KtReferenceExpression) {
                    PsiElement parentOfParent = parent.getParent();
                    KtQualifiedExpression qualifiedExpression = PsiTreeUtil.getParentOfType(element, KtQualifiedExpression.class);
                    if (qualifiedExpression != null && qualifiedExpression.getSelectorExpression() == parentOfParent) {
                        return false;
                    }
                }
            }
        }

        return element != null && isInContext(element);
    }

    @Override
    public @Nullable SyntaxHighlighter createHighlighter() {
        return new KotlinHighlighter();
    }

    protected boolean isCommentInContext() {
        return false;
    }

    protected abstract boolean isInContext(@NotNull PsiElement element);

    public static class Generic extends KotlinTemplateContextType {
        public Generic() {
            super(KotlinLiveTemplatesBundle.message("template.context.type.generic"));
        }

        @Override
        protected boolean isInContext(@NotNull PsiElement element) {
            return true;
        }

        @Override
        protected boolean isCommentInContext() {
            return true;
        }
    }

    public static class TopLevel extends KotlinTemplateContextType {
        public TopLevel() {
            super(KotlinLiveTemplatesBundle.message("template.context.type.top.level"));
        }

        @Override
        protected boolean isInContext(@NotNull PsiElement element) {
            PsiElement e = element;
            while (e != null) {
                if (e instanceof KtModifierList) {
                    // skip property/function/class or object which is owner of modifier list
                    e = e.getParent();
                    if (e != null) {
                        e = e.getParent();
                    }
                    continue;
                }
                if (e instanceof KtProperty || e instanceof KtNamedFunction || e instanceof KtClassOrObject) {
                    return false;
                }
                if (e instanceof KtScriptInitializer) {
                    return false;
                }
                e = e.getParent();
            }
            return true;
        }
    }

    public static class ObjectDeclaration extends KotlinTemplateContextType {
        public ObjectDeclaration() {
            super(KotlinLiveTemplatesBundle.message("template.context.type.object"));
        }

        @Override
        protected boolean isInContext(@NotNull PsiElement element) {
            KtObjectDeclaration objectDeclaration = getParentClassOrObject(element, KtObjectDeclaration.class);
            return objectDeclaration != null && !objectDeclaration.isObjectLiteral();
        }
    }

    public static class Class extends KotlinTemplateContextType {
        public Class() {
            super(KotlinLiveTemplatesBundle.message("template.context.type.class"));
        }

        @Override
        protected boolean isInContext(@NotNull PsiElement element) {
            return getParentClassOrObject(element, KtClassOrObject.class) != null;
        }
    }

    public static class Statement extends KotlinTemplateContextType {
        public Statement() {
            super(KotlinLiveTemplatesBundle.message("template.context.type.statement"));
        }

        @Override
        protected boolean isInContext(@NotNull PsiElement element) {
            PsiElement parentStatement = PsiTreeUtil.findFirstParent(element, e ->
                    e instanceof KtExpression && KtPsiUtil.isStatementContainer(e.getParent()));

            if (parentStatement == null) return false;

            // We are in the leftmost position in parentStatement
            return element.getTextOffset() == parentStatement.getTextOffset();
        }
    }

    public static class Expression extends KotlinTemplateContextType {
        public Expression() {
            super(KotlinLiveTemplatesBundle.message("template.context.type.expression"));
        }

        @Override
        protected boolean isInContext(@NotNull PsiElement element) {
            return element.getParent() instanceof KtExpression && !(element.getParent() instanceof KtConstantExpression) &&
                   !(element.getParent().getParent() instanceof KtDotQualifiedExpression)
                   && !(element.getParent() instanceof KtParameter);
        }
    }

    public static class Comment extends KotlinTemplateContextType {
        public Comment() {
            super(KotlinLiveTemplatesBundle.message("template.context.type.comment"));
        }

        @Override
        protected boolean isInContext(@NotNull PsiElement element) {
            return false;
        }

        @Override
        protected boolean isCommentInContext() {
            return true;
        }
    }

    private static <T extends PsiElement> T getParentClassOrObject(
            @NotNull PsiElement element,
            @NotNull java.lang.Class<? extends T> klass
    ) {
        PsiElement e = element;
        while (e != null && !klass.isInstance(e)) {
            if (e instanceof KtModifierList) {
                // skip property/function/class or object which is owner of modifier list
                e = e.getParent();
                if (e != null) {
                    e = e.getParent();
                }
                continue;
            }
            if (e instanceof KtProperty || e instanceof KtNamedFunction) {
                return null;
            }
            e = e.getParent();
        }

        //noinspection unchecked
        return (T) e;
    }
}
