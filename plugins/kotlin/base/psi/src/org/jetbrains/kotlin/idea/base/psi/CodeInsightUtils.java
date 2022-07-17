// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.psi;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.text.CharArrayUtil;
import kotlin.collections.ArraysKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtBlockExpression;
import org.jetbrains.kotlin.types.KotlinType;

import static org.jetbrains.kotlin.builtins.KotlinBuiltIns.*;

public class CodeInsightUtils {
    @Nullable
    public static PsiElement getElementAtOffsetIgnoreWhitespaceBefore(@NotNull PsiFile file, int offset) {
        PsiElement element = file.findElementAt(offset);
        if (element instanceof PsiWhiteSpace) {
            return file.findElementAt(element.getTextRange().getEndOffset());
        }
        return element;
    }

    @Nullable
    public static PsiElement getElementAtOffsetIgnoreWhitespaceAfter(@NotNull PsiFile file, int offset) {
        PsiElement element = file.findElementAt(offset - 1);
        if (element instanceof PsiWhiteSpace) {
            return file.findElementAt(element.getTextRange().getStartOffset() - 1);
        }
        return element;
    }

    @Nullable
    public static String defaultInitializer(KotlinType type) {
        if (type.isMarkedNullable()) {
            return "null";
        }
        else if (isInt(type) || isLong(type) || isShort(type) || isByte(type)) {
            return "0";
        }
        else if (isFloat(type)) {
            return "0.0f";
        }
        else if (isDouble(type)) {
            return "0.0";
        }
        else if (isChar(type)) {
            return "'\\u0000'";
        }
        else if (isBoolean(type)) {
            return "false";
        }
        else if (isUnit(type)) {
            return "Unit";
        }
        else if (isString(type)) {
            return "\"\"";
        }

        return null;
    }

    private CodeInsightUtils() {
    }

    @Nullable
    public static Integer getStartLineOffset(@NotNull PsiFile file, int line) {
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) return null;

        if (line >= document.getLineCount()) {
            return null;
        }

        int lineStartOffset = document.getLineStartOffset(line);
        return CharArrayUtil.shiftForward(document.getCharsSequence(), lineStartOffset, " \t");
    }

    @Nullable
    public static Integer getEndLineOffset(@NotNull PsiFile file, int line) {
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) return null;

        if (line >= document.getLineCount()) {
            return null;
        }

        int lineStartOffset = document.getLineEndOffset(line);
        return CharArrayUtil.shiftBackward(document.getCharsSequence(), lineStartOffset, " \t");
    }

    @NotNull
    public static PsiElement getTopmostElementAtOffset(@NotNull PsiElement element, int offset) {
        do {
            PsiElement parent = element.getParent();
            if (parent == null
                || (parent.getTextOffset() < offset)
                || parent instanceof KtBlockExpression
                || parent instanceof PsiFile
            ) {
                break;
            }

            element = parent;
        }
        while(true);

        return element;
    }

    @NotNull
    public static PsiElement getTopParentWithEndOffset(@NotNull PsiElement element, @NotNull Class<?> stopAt) {
        int endOffset = element.getTextOffset() + element.getTextLength();

        do {
            PsiElement parent = element.getParent();
            if (parent == null || (parent.getTextOffset() + parent.getTextLength()) != endOffset) {
                break;
            }
            element = parent;

            if (stopAt.isInstance(element)) {
                break;
            }
        }
        while(true);

        return element;
    }


    @SafeVarargs
    @Nullable
    public static <T> T getTopmostElementAtOffset(@NotNull PsiElement element, int offset, @NotNull Class<? extends T> @NotNull ... classes) {
        T lastElementOfType = null;
        if (anyIsInstance(element, classes)) {
            lastElementOfType = (T) element;
        }
        do {
            PsiElement parent = element.getParent();
            if (parent == null || (parent.getTextOffset() < offset) || parent instanceof KtBlockExpression) {
                break;
            }
            if (anyIsInstance(parent, classes)) {
                lastElementOfType = (T) parent;
            }
            element = parent;
        }
        while(true);

        return lastElementOfType;
    }

    private static <T> boolean anyIsInstance(PsiElement finalElement, @NotNull Class<? extends T> @NotNull [] klass) {
        return ArraysKt.any(klass, aClass -> aClass.isInstance(finalElement));
    }
}
