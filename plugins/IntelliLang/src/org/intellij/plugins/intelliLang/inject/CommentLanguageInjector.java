// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.injection.general.Injection;
import com.intellij.lang.injection.general.LanguageInjectionContributor;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author gregsh
 */
final class CommentLanguageInjector implements LanguageInjectionContributor {

  @Override
  public @Nullable Injection getInjection(@NotNull PsiElement context) {
    if (!(context instanceof PsiLanguageInjectionHost host) || context instanceof PsiComment) return null;
    if (!host.isValidHost()) return null;

    boolean applicableFound = false;
    for (LanguageInjectionSupport support : InjectorUtils.getActiveInjectionSupports()) {
      if (!support.isApplicableTo(host)) continue;
      applicableFound = true;
      //noinspection deprecation
      if (!support.useDefaultCommentInjector()) continue;
      BaseInjection injection = support.findCommentInjection(host, null);
      if (isCompatible(injection, context)) {
        return injection;
      }
    }

    if (applicableFound) return null;

    BaseInjection commentInjection = InjectorUtils.findCommentInjection(host, "comment", null);
    if (!isCompatible(commentInjection, context)) return null;
    return commentInjection;
  }

  private static boolean isCompatible(@Nullable BaseInjection injection, @NotNull PsiElement context) {
    if (injection == null) return false;
    if (injection.getInjectionPlaces().length == 0) return true; // it seems that "placeless" injections shouldn't be checked
    return injection.acceptsPsiElement(context);
  }
}
