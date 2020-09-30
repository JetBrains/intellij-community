// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  public static boolean isEscape(@NotNull final EditorConfigCharClassLetter letter) {
    return letter.textContains('\\');
  }

  public static boolean isValidEscape(@NotNull final EditorConfigCharClassLetter letter) {
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

  @Nullable
  public static EditorConfigDescriptor getDescriptor(@NotNull final EditorConfigOptionValuePair pair, final boolean smart) {
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


  @Nullable
  public static EditorConfigDescriptor getDescriptor(@NotNull final EditorConfigOptionValueList list, final boolean smart) {
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

  @NotNull
  public static EditorConfigDescribableElement getFirst(@NotNull final EditorConfigOptionValuePair pair) {
    final Optional<PsiElement> first = Arrays.stream(pair.getChildren())
      .filter(child -> child instanceof EditorConfigDescribableElement)
      .findFirst();

    if (first.isEmpty()) {
      throw new IllegalStateException();
    }

    return (EditorConfigDescribableElement)first.get();
  }

  @NotNull
  public static EditorConfigDescribableElement getSecond(@NotNull final EditorConfigOptionValuePair pair) {
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
