// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.psi.impl;

import com.intellij.psi.PsiElement;
import kotlin.text.StringsKt;
import org.editorconfig.language.psi.EditorConfigCharClassLetter;
import org.editorconfig.language.psi.EditorConfigOptionValuePair;
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;

public final class EditorConfigPsiImplUtils {
  private EditorConfigPsiImplUtils() {}

  public static final String VALID_ESCAPES = " \r\n\t\\#;!?*[]{}";

  // ---- ---- Charclass utils ---- ----

  public static boolean isEscape(final @NotNull EditorConfigCharClassLetter letter) {
    return letter.textContains('\\');
  }

  public static boolean isValidEscape(final @NotNull EditorConfigCharClassLetter letter) {
    if (!letter.isEscape()) return false;

    final int length = letter.getTextLength();
    if (length == 3) {
      return letter.textMatches("\\\r\n");
    }

    if (length != 2) return false;

    final String text = letter.getText();
    if (text.charAt(0) != '\\') return false;
    return StringsKt.contains(VALID_ESCAPES, text.charAt(1), false);
  }

  // ---- ---- Value pair utils ---- ----

  public static @NotNull EditorConfigDescribableElement getFirst(final @NotNull EditorConfigOptionValuePair pair) {
    final Optional<PsiElement> first = Arrays.stream(pair.getChildren())
      .filter(child -> child instanceof EditorConfigDescribableElement)
      .findFirst();

    if (first.isEmpty()) {
      throw new IllegalStateException();
    }

    return (EditorConfigDescribableElement)first.get();
  }

  public static @NotNull EditorConfigDescribableElement getSecond(final @NotNull EditorConfigOptionValuePair pair) {
    final Optional<PsiElement> second = Arrays.stream(pair.getChildren())
      .filter(child -> child instanceof EditorConfigDescribableElement)
      .skip(1)
      .findFirst();

    if (second.isEmpty()) {
      throw new IllegalStateException();
    }

    return (EditorConfigDescribableElement)second.get();
  }
}
