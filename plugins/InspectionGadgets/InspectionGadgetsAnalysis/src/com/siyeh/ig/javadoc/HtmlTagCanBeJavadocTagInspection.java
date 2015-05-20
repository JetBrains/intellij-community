/*
 * Copyright 2011-2015 Bas Leijdekkers
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
import com.intellij.openapi.util.text.StringUtil;
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
      int startTag = range.getEndOffset();
      @NonNls String text = element.getText();
      if (!"<code>".equalsIgnoreCase(text.substring(startOffset, startTag))) {
        return;
      }
      @NonNls final StringBuilder newCommentText = new StringBuilder("{@code");
      int endTag = StringUtil.indexOfIgnoreCase(text, "</code>", startTag);
      while (endTag < 0) {
        appendElementText(text, startTag, text.length(), newCommentText);
        element = element.getNextSibling();
        if (element == null) return;
        startTag = 0;
        text = element.getText();
        endTag = StringUtil.indexOfIgnoreCase(text, "</code>", 0);
      }
      appendElementText(text, startTag, endTag, newCommentText);
      newCommentText.append('}');
      final int replaceEndOffset = element.getTextOffset() + endTag + 7;
      final String oldText = document.getText(new TextRange(replaceStartOffset, replaceEndOffset));
      if (!StringUtil.startsWithIgnoreCase(oldText, "<code>") || !StringUtil.endsWithIgnoreCase(oldText, "</code>")) { // sanity check
        return;
      }
      document.replaceString(replaceStartOffset, replaceEndOffset, newCommentText);
    }

    private static void appendElementText(String text, int startOffset, int endOffset, StringBuilder out) {
      if (out.length() == "{@code".length() && endOffset - startOffset > 0 && !Character.isWhitespace(text.charAt(startOffset))) {
        out.append(' ');
      }
      out.append(text, startOffset, endOffset);
    }
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new HtmlTagCanBeJavaDocTagVisitor();
  }

  private static class HtmlTagCanBeJavaDocTagVisitor extends BaseInspectionVisitor {
    @Override
    public void visitDocToken(PsiDocToken token) {
      super.visitDocToken(token);
      final IElementType tokenType = token.getTokenType();
      if (!JavaDocTokenType.DOC_COMMENT_DATA.equals(tokenType)) {
        return;
      }
      @NonNls final String text = token.getText();
      int startIndex = 0;
      while (true) {
        startIndex = StringUtil.indexOfIgnoreCase(text, "<code>", startIndex);
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
      final int endOffset1 = StringUtil.indexOfIgnoreCase(text, "</code>", offset);
      if (endOffset1 >= 0) {
        final int startOffset1 = StringUtil.indexOfIgnoreCase(text, "<code>", offset);
        return startOffset1 < 0 || startOffset1 > endOffset1;
      }
      PsiElement sibling = element.getNextSibling();
      while (sibling != null) {
        @NonNls final String text1 = sibling.getText();
        final int endOffset = StringUtil.indexOfIgnoreCase(text1, "</code>", 0);
        if (endOffset >= 0) {
          final int startOffset = StringUtil.indexOfIgnoreCase(text1, "<code>", 0);
          return startOffset < 0 || startOffset > endOffset;
        }
        sibling = sibling.getNextSibling();
      }
      return false;
    }
  }
}
