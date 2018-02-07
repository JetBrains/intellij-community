/*
 * Copyright 2011-2018 Bas Leijdekkers
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
import com.intellij.psi.javadoc.PsiInlineDocTag;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("html.tag.can.be.javadoc.tag.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
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
      final String s = text.substring(startOffset, endOffset);
      out.append(StringUtil.unescapeXml(s));
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
      while (element != null) {
        @NonNls final String text = element.getText();
        final int endIndex = StringUtil.indexOfIgnoreCase(text, "</code>", offset);
        if (containsHtmlTag(text, offset, endIndex >= 0 ? endIndex : text.length())) {
          return false;
        }
        if (endIndex >= 0) {
          return true;
        }
        offset = 0;
        element = element.getNextSibling();
        if (element instanceof PsiInlineDocTag) {
          return false;
        }
      }
      return false;
    }
  }

  private static final Pattern START_TAG_PATTERN = Pattern.compile("<([a-zA-Z])+([^>])*>");

  private static boolean containsHtmlTag(String text, int startIndex, int endIndex) {
    final Matcher matcher = START_TAG_PATTERN.matcher(text);
    if (matcher.find(startIndex)) {
      return matcher.start() < endIndex;
    }
    return false;
  }
}
