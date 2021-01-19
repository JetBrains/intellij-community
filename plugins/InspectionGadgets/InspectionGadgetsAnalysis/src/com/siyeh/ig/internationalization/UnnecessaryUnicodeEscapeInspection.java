// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.internationalization;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryUnicodeEscapeInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final Character c = (Character)infos[0];
    if (c == '\n') {
      return InspectionGadgetsBundle.message("unnecessary.unicode.escape.problem.newline.descriptor");
    }
    else if (c == '\t') {
      return InspectionGadgetsBundle.message("unnecessary.unicode.escape.problem.tab.descriptor");
    }
    return InspectionGadgetsBundle.message("unnecessary.unicode.escape.problem.descriptor", c);
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryUnicodeEscapeFix(((Character) infos[0]).charValue(), (RangeMarker)infos[1]);
  }

  private static class UnnecessaryUnicodeEscapeFix extends InspectionGadgetsFix {

    private final char c;
    // It holds the original document but we take care not to use it
    @SafeFieldForPreview
    private final RangeMarker myRangeMarker;

    UnnecessaryUnicodeEscapeFix(char c, RangeMarker rangeMarker) {
      this.c = c;
      myRangeMarker = rangeMarker;
    }

    @NotNull
    @Override
    public String getName() {
      if (c == '\n') {
        return InspectionGadgetsBundle.message("unnecessary.unicode.escape.fix.text");
      }
      else if (c == '\t') {
        return InspectionGadgetsBundle.message("unnecessary.unicode.escape.fix.text");
      }
      return CommonQuickFixBundle.message("fix.replace.with.x", c);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.unicode.escape.fix.family.name");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      Document document = descriptor.getPsiElement().getContainingFile().getViewProvider().getDocument();
      if (document != null) {
        document.replaceString(myRangeMarker.getStartOffset(), myRangeMarker.getEndOffset(), String.valueOf(c));
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryUnicodeEscapeVisitor();
  }

  private class UnnecessaryUnicodeEscapeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitFile(@NotNull PsiFile file) {
      super.visitFile(file);
      if (InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file) || !file.isPhysical()) {
        return;
      }
      final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
      if (document == null) {
        return;
      }
      final VirtualFile virtualFile = file.getVirtualFile();
      final String text = file.getText();
      final Charset charset = LoadTextUtil.extractCharsetFromFileContent(file.getProject(), virtualFile, text);
      final CharsetEncoder encoder = charset.newEncoder().onUnmappableCharacter(CodingErrorAction.REPORT);
      final CharBuffer charBuffer = CharBuffer.allocate(1);
      final ByteBuffer byteBuffer = ByteBuffer.allocate(10);
      final int length = text.length();
      for (int i = 0; i < length; i++) {
        final char c = text.charAt(i);
        if (c != '\\') {
          continue;
        }
        boolean isEscape = true;
        int previousChar = i - 1;
        while (previousChar >= 0 && text.charAt(previousChar) == '\\') {
          isEscape = !isEscape;
          previousChar--;
        }
        if (!isEscape) {
          continue;
        }
        int nextChar = i + 1;
        while (nextChar < length && text.charAt(nextChar) == 'u') {
          nextChar++; // \\uuuu0061 is a legal Unicode escape
        }
        if (nextChar == i + 1 || nextChar + 3 >= length) {
          continue;
        }
        if (StringUtil.isHexDigit(text.charAt(nextChar)) &&
            StringUtil.isHexDigit(text.charAt(nextChar + 1)) &&
            StringUtil.isHexDigit(text.charAt(nextChar + 2)) &&
            StringUtil.isHexDigit(text.charAt(nextChar + 3))) {
          final int escapeEnd = nextChar + 4;
          final char d = (char)Integer.parseInt(text.substring(nextChar, escapeEnd), 16);
          if (d == '\uFFFD') {
            // this character is used as a replacement when a unicode character can't be displayed
            // replacing the escape with the character may cause confusion, so ignore it.
            continue;
          }
          final int type = Character.getType(d);
          if (type == Character.CONTROL && d != '\n' && d != '\t') {
            continue;
          }
          else if (type == Character.FORMAT ||
                   type == Character.PRIVATE_USE ||
                   type == Character.SURROGATE ||
                   type == Character.UNASSIGNED ||
                   type == Character.LINE_SEPARATOR ||
                   type == Character.PARAGRAPH_SEPARATOR) {
            continue;
          }
          if (type == Character.SPACE_SEPARATOR && d != ' ') {
            continue;
          }
          byteBuffer.clear();
          charBuffer.clear();
          charBuffer.put(d).rewind();
          final CoderResult coderResult = encoder.encode(charBuffer, byteBuffer, true);
          if (coderResult.isError()) {
            continue;
          }
          final PsiElement element = file.findElementAt(i);
          if (element != null && isSuppressedFor(element)) {
            return;
          }
          final RangeMarker rangeMarker = document.createRangeMarker(i, escapeEnd);
          registerErrorAtOffset(file, i, escapeEnd - i, Character.valueOf(d), rangeMarker);
        }
      }
    }
  }
}
