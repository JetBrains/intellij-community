// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.upDownMover;

import com.intellij.codeInsight.editorActions.moveUpDown.LineMover;
import com.intellij.codeInsight.editorActions.moveUpDown.LineRange;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.kotlin.idea.util.FormatterUtilKt;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.*;

public abstract class AbstractKotlinUpDownMover extends LineMover {
    protected AbstractKotlinUpDownMover() {
    }

    protected abstract boolean checkSourceElement(@NotNull PsiElement element);

    protected abstract LineRange getElementSourceLineRange(
            @NotNull PsiElement element,
            @NotNull Editor editor,
            @NotNull LineRange oldRange
    );

    protected @Nullable LineRange getSourceRange(
            @NotNull PsiElement firstElement,
            @NotNull PsiElement lastElement,
            @NotNull Editor editor,
            LineRange oldRange
    ) {
        PsiElement parent = PsiTreeUtil.findCommonParent(firstElement, lastElement);

        int topExtension = 0;
        int bottomExtension = 0;

        if (parent instanceof KtFunctionLiteral) {
            KtBlockExpression block = ((KtFunctionLiteral) parent).getBodyExpression();
            if (block != null) {
                PsiElement comment = null;

                boolean extendDown = false;
                if (checkCommentAtBlockBound(firstElement, lastElement, block)) {
                    comment = lastElement;
                    extendDown = true;
                    lastElement = block.getLastChild();
                }
                else if (checkCommentAtBlockBound(lastElement, firstElement, block)) {
                    comment = firstElement;
                    firstElement = block.getFirstChild();
                }

                if (comment != null) {
                    int extension = FormatterUtilKt.getLineCount(comment);
                    if (extendDown) {
                        bottomExtension = extension;
                    }
                    else {
                        topExtension = extension;
                    }
                }


                parent = PsiTreeUtil.findCommonParent(firstElement, lastElement);
            }
        }

        if (parent == null) return null;

        Pair<PsiElement, PsiElement> originalRange = getElementRange(parent, firstElement, lastElement);

        if (!checkSourceElement(originalRange.first) || !checkSourceElement(originalRange.second)) return null;

        LineRange lineRange1 = getElementSourceLineRange(originalRange.first, editor, oldRange);
        if (lineRange1 == null) return null;

        LineRange lineRange2 = getElementSourceLineRange(originalRange.second, editor, oldRange);
        if (lineRange2 == null) return null;

        LineRange parentLineRange = getElementSourceLineRange(parent, editor, oldRange);

        LineRange sourceRange = new LineRange(lineRange1.startLine - topExtension, lineRange2.endLine + bottomExtension);

        if (parentLineRange != null
            && sourceRange.startLine == parentLineRange.startLine
            && sourceRange.endLine == parentLineRange.endLine
        ) {
            sourceRange.firstElement = sourceRange.lastElement = parent;
        }
        else {
            sourceRange.firstElement = originalRange.first;
            sourceRange.lastElement = originalRange.second;
        }

        return sourceRange;
    }

    protected static int getElementLine(@Nullable PsiElement element, @NotNull Editor editor, boolean first) {
        if (element == null) return -1;

        Document doc = editor.getDocument();
        TextRange spaceRange = element.getTextRange();

        return first ? doc.getLineNumber(spaceRange.getStartOffset()) : doc.getLineNumber(spaceRange.getEndOffset());
    }

    protected static PsiElement getLastNonWhiteSiblingInLine(@Nullable PsiElement element, @NotNull Editor editor, boolean down) {
        if (element == null) return null;

        int line = getElementLine(element, editor, down);

        PsiElement lastElement = element;
        while (true) {
            if (lastElement == null) return null;
            PsiElement sibling = firstNonWhiteSibling(lastElement, down);
            if (getElementLine(sibling, editor, down) == line) {
                lastElement = sibling;
            }
            else break;
        }

        return lastElement;
    }

    protected static @Nullable KtAnnotationEntry getParentFileAnnotationEntry(@Nullable PsiElement element) {
        if (element == null) return null;

        KtAnnotationEntry annotationEntry = PsiTreeUtil.getParentOfType(element, KtAnnotationEntry.class);
        if (annotationEntry == null) return null;

        KtAnnotationUseSiteTarget useSiteTarget = annotationEntry.getUseSiteTarget();
        if (useSiteTarget == null) return null;

        ASTNode node = useSiteTarget.getNode().getFirstChildNode();
        if (node == null) return null;
        if (node.getElementType() != KtTokens.FILE_KEYWORD) return null;

        return annotationEntry;
    }

    private static boolean checkCommentAtBlockBound(PsiElement blockElement, PsiElement comment, KtBlockExpression block) {
        return PsiTreeUtil.isAncestor(block, blockElement, true) && comment instanceof PsiComment;
    }

    protected static @Nullable PsiElement getSiblingOfType(@NotNull PsiElement element, boolean down, @NotNull Class<? extends PsiElement> type) {
        return down ? PsiTreeUtil.getNextSiblingOfType(element, type) : PsiTreeUtil.getPrevSiblingOfType(element, type);
    }

    protected static @Nullable PsiElement firstNonWhiteSibling(@NotNull LineRange lineRange, boolean down) {
        return firstNonWhiteElement(down ? lineRange.lastElement.getNextSibling() : lineRange.firstElement.getPrevSibling(), down);
    }

    protected static @Nullable PsiElement firstNonWhiteSibling(@NotNull PsiElement element, boolean down) {
        return firstNonWhiteElement(down ? element.getNextSibling() : element.getPrevSibling(), down);
    }

    @Override
    public boolean checkAvailable(@NotNull Editor editor, @NotNull PsiFile file, @NotNull MoveInfo info, boolean down) {
        return file.getFileType() instanceof KotlinFileType && super.checkAvailable(editor, file, info, down);
    }
}
