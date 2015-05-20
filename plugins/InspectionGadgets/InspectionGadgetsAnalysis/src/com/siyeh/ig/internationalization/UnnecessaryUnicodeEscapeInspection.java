/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.internationalization;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryUnicodeEscapeInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unnecessary.unicode.escape.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final Character c = (Character)infos[0];
    return InspectionGadgetsBundle.message("unnecessary.unicode.escape.problem.descriptor", c);
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryUnicodeEscapeFix(((Character) infos[0]).charValue());
  }

  private static class UnnecessaryUnicodeEscapeFix extends InspectionGadgetsFix {

    private final char c;

    public UnnecessaryUnicodeEscapeFix(char c) {
      this.c = c;
    }

    @NotNull
    @Override
    public String getName() {
      return "Replace with '" + c + "'";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with character";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final TextRange textRange = descriptor.getTextRangeInElement();
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiFile)) {
        return;
      }
      final PsiFile file = (PsiFile)element;
      final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      if (document == null) {
        return;
      }
      document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), String.valueOf(c));
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryUnicodeEscapeVisitor();
  }

  private class UnnecessaryUnicodeEscapeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitFile(PsiFile file) {
      super.visitFile(file);
      if (InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file) || !file.isPhysical()) {
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
        int nextChar = i;
        do {
          nextChar++;
          if (nextChar >= length) {
            break;
          }
        }
        while (text.charAt(nextChar) == 'u'); // \uuuu0061 is a legal unicode escape
        if (nextChar == i + 1 || nextChar + 3 >= length) {
          continue;
        }
        if (StringUtil.isHexDigit(text.charAt(nextChar)) &&
            StringUtil.isHexDigit(text.charAt(nextChar + 1)) &&
            StringUtil.isHexDigit(text.charAt(nextChar + 2)) &&
            StringUtil.isHexDigit(text.charAt(nextChar + 3))) {
          final int escapeEnd = nextChar + 4;
          final char d = (char)Integer.parseInt(text.substring(nextChar, escapeEnd), 16);
          if (Character.isISOControl(d)) {
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
          registerErrorAtOffset(file, i, escapeEnd - i, Character.valueOf(d));
        }
      }
    }
  }
}
