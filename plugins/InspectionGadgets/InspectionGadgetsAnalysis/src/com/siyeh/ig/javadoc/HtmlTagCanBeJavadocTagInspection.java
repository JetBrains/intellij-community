/*
 * Copyright 2011-2013 Bas Leijdekkers
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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
    return InspectionGadgetsBundle.message("html.tag.can.be.javadoc.tag.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("html.tag.can.be.javadoc.tag.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new HtmlTagCanBeJavaDocTagFix();
  }

  private static class HtmlTagCanBeJavaDocTagFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("html.tag.can.be.javadoc.tag.quickfix");
    }
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final TextRange range = descriptor.getTextRangeInElement();
      PsiElement element = descriptor.getPsiElement();
      final PsiFile file = PsiTreeUtil.getParentOfType(element, PsiFile.class);
      if (file == null) {
        return;
      }
      final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      if (document == null) {
        return;
      }
      final int startOffset = range.getStartOffset();
      final int replaceStartOffset = element.getTextOffset() + startOffset;
      final int endOffset = range.getEndOffset();
      @NonNls String text = element.getText();
      if (!"<code>".equals(text.substring(startOffset, endOffset))) {
        return;
      }
      @NonNls final StringBuilder newCommentText = new StringBuilder("{@code");
      int endTag = text.indexOf("</code>", startOffset);
      boolean first = true;
      while (endTag < 0) {
        appendElementText(text, endOffset, text.length(), first, newCommentText);
        first = false;
        element = element.getNextSibling();
        if (element == null) return;
        text = element.getText();
        endTag = text.indexOf("</code>");
      }
      appendElementText(text, endOffset, endTag, first, newCommentText);
      newCommentText.append('}');
      final int replaceEndOffset = element.getTextOffset() + endTag + 7;
      final String oldText = document.getText(new TextRange(replaceStartOffset, replaceEndOffset));
      if (!oldText.startsWith("<code>") || !oldText.endsWith("</code>")) { // sanity check
        return;
      }
      document.replaceString(replaceStartOffset, replaceEndOffset, newCommentText);
    }

    private static void appendElementText(String text, int startOffset, int endOffset, boolean first, StringBuilder out) {
      if (first) {
        final String substring = text.substring(startOffset, endOffset);
        if (!substring.isEmpty() && !Character.isWhitespace(substring.charAt(0))) {
          out.append(' ');
        }
        out.append(substring);
      }
      else {
        out.append(text.substring(0, endOffset));
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new HtmlTagCanBeJavaDocTagVisitor();
  }

  private static class HtmlTagCanBeJavaDocTagVisitor extends BaseInspectionVisitor {

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
        if (hasMatchingCloseTag(token, startIndex + 6)) {
          registerErrorAtOffset(token, startIndex, 6);
        }
        startIndex++;
      }
    }

    private static boolean hasMatchingCloseTag(PsiElement element, int offset) {
      @NonNls final String text = element.getText();
      final int endOffset1 = text.indexOf("</code>", offset);
      if (endOffset1 >= 0) {
        final int startOffset1 = text.indexOf("<code>", offset);
        return startOffset1 < 0 || startOffset1 > endOffset1;
      }
      PsiElement sibling = element.getNextSibling();
      while (sibling != null) {
        @NonNls final String text1 = sibling.getText();
        final int endOffset = text1.indexOf("</code>");
        if (endOffset >= 0) {
          final int startOffset = text1.indexOf("<code>");
          return startOffset < 0 || startOffset > endOffset;
        }
        sibling = sibling.getNextSibling();
      }
      return false;
    }
  }
}
