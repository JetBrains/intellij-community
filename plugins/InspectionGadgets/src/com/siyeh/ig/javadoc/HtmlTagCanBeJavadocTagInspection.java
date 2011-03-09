/*
 * Copyright 2011 Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class HtmlTagCanBeJavadocTagInspection extends BaseInspection {

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "html.tag.can.be.javadoc.tag.display.name");
    }

    @NotNull
    @Override
    protected String buildErrorString(Object... infos) {
        final boolean startTag = ((Boolean) infos[0]).booleanValue();
        if (startTag) {
            return InspectionGadgetsBundle.message(
                    "html.tag.can.be.javadoc.tag.problem.descriptor1");
        } else {
            return InspectionGadgetsBundle.message(
                    "html.tag.can.be.javadoc.tag.problem.descriptor2");
        }
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final int offset = ((Integer) infos[1]).intValue();
        return new HtmlTagCanBeJavaDocTagFix(offset);
    }

    private static class HtmlTagCanBeJavaDocTagFix
            extends InspectionGadgetsFix {

        private final int startIndex;

        public HtmlTagCanBeJavaDocTagFix(int startIndex) {
            this.startIndex = startIndex;
        }

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "html.tag.can.be.javadoc.tag.quickfix");
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiDocComment comment =
                    PsiTreeUtil.getParentOfType(element, PsiDocComment.class);
            if (comment == null) {
                return;
            }
            @NonNls final StringBuilder newCommentText = new StringBuilder();
            buildNewCommentText(comment, element, newCommentText);
            final PsiElementFactory factory =
                    JavaPsiFacade.getElementFactory(project);
            final PsiDocComment newComment =
                    factory.createDocCommentFromText(newCommentText.toString());
            comment.replace(newComment);
        }

        private void buildNewCommentText(PsiElement element,
                                         PsiElement elementToReplace,
                                         @NonNls StringBuilder newCommentText) {
            final PsiElement[] children = element.getChildren();
            if (children.length != 0) {
                for (PsiElement child : children) {
                    buildNewCommentText(child, elementToReplace,
                            newCommentText);
                }
                return;
            }
            @NonNls final String text = element.getText();
            if (element != elementToReplace) {
                newCommentText.append(text);
            } else {
                newCommentText.append(text.substring(0, startIndex));
                newCommentText.append("{@code ");
                final int endIndex = text.indexOf("</code>", startIndex);
                if (endIndex >= 0) {
                    final String codeText =
                            text.substring(startIndex + 6, endIndex);
                    newCommentText.append(codeText);
                            //StringUtil.replace(codeText, "}", "&#125;"));
                    newCommentText.append('}');
                    newCommentText.append(text.substring(endIndex + 7));
                } else {
                    final String codeText = text.substring(startIndex + 6);
                    newCommentText.append(codeText);
                            //StringUtil.replace(codeText, "}", "&#125;"));
                    newCommentText.append('}');
                }
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new HtmlTagCanBeJavaDocTagVisitor();
    }

    private static class HtmlTagCanBeJavaDocTagVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitDocToken(PsiDocToken token) {
            super.visitDocToken(token);
            if (!PsiUtil.isLanguageLevel5OrHigher(token)) {
                return;
            }
            final IElementType tokenType = token.getTokenType();
            if (!JavaDocTokenType.DOC_COMMENT_DATA.equals(tokenType)) {
                return;
            }
            @NonNls final String text = token.getText();
            int startIndex = 0;
            while (true) {
                startIndex = text.indexOf("<code>", startIndex);
                if (startIndex < 0) {
                    return;
                }
                registerError(token, startIndex, 6, Boolean.TRUE,
                        Integer.valueOf(startIndex));
                final int endIndex = text.indexOf("</code>", startIndex);
                if (endIndex < 0) {
                    return;
                }
                registerError(token, endIndex, 7, Boolean.FALSE,
                        Integer.valueOf(startIndex));
                startIndex++;
            }
        }
    }
}
