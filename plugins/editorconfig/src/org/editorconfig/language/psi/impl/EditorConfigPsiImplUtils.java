// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.psi.impl;

import com.intellij.psi.PsiElement;
import kotlin.text.StringsKt;
import org.editorconfig.language.highlighting.EditorConfigSyntaxHighlighter;
import org.editorconfig.language.psi.EditorConfigCharClassLetter;
import org.editorconfig.language.psi.EditorConfigOptionValueList;
import org.editorconfig.language.psi.EditorConfigOptionValuePair;
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement;
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

public final class EditorConfigPsiImplUtils {
  private EditorConfigPsiImplUtils() {}

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
    return StringsKt.contains(EditorConfigSyntaxHighlighter.VALID_ESCAPES, text.charAt(1), false);
  }

  // ---- ---- Value pair utils ---- ----

  public static @Nullable EditorConfigDescriptor getDescriptor(final @NotNull EditorConfigOptionValuePair pair, final boolean smart) {
    final EditorConfigDescribableElement parent = pair.getDescribableParent();
    if (parent == null) {
      return null;
    }

    final EditorConfigDescriptor parentDescriptor = parent.getDescriptor(smart);
    if (parentDescriptor == null) {
      return null;
    }

    final EditorConfigPairDescriptorFinderVisitor finder = new EditorConfigPairDescriptorFinderVisitor();
    parentDescriptor.accept(finder);
    return finder.getDescriptor();
  }

  // ---- ---- Value list utils ---- ----


  public static @Nullable EditorConfigDescriptor getDescriptor(final @NotNull EditorConfigOptionValueList list, final boolean smart) {
    final EditorConfigDescribableElement parent = list.getDescribableParent();
    if (parent == null) {
      return null;
    }

    final EditorConfigDescriptor parentDescriptor = parent.getDescriptor(smart);
    if (parentDescriptor == null) {
      return null;
    }

    final EditorConfigListDescriptorFinderVisitor finder = new EditorConfigListDescriptorFinderVisitor(list);
    parentDescriptor.accept(finder);
    return finder.getDescriptor();
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
