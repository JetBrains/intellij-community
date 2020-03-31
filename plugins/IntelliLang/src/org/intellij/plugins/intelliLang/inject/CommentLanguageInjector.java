// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.util.ArrayUtil;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author gregsh
 */
public class CommentLanguageInjector implements MultiHostInjector {
  private final LanguageInjectionSupport myInjectorSupport = new AbstractLanguageInjectionSupport() {
    @NotNull
    @Override
    public String getId() {
      return "comment";
    }

    @Override
    public boolean isApplicableTo(PsiLanguageInjectionHost host) {
      return true;
    }

    @Override
    public Class @NotNull [] getPatternClasses() {
      return ArrayUtil.EMPTY_CLASS_ARRAY;
    }
  };

  public CommentLanguageInjector(@NotNull Project project) {
    Configuration.getProjectInstance(project);
  }

  @Override
  @NotNull
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Collections.singletonList(PsiLanguageInjectionHost.class);
  }

  @Override
  public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull final PsiElement context) {
    if (!(context instanceof PsiLanguageInjectionHost) || context instanceof PsiComment) return;
    if (!((PsiLanguageInjectionHost)context).isValidHost()) return;
    PsiLanguageInjectionHost host = (PsiLanguageInjectionHost)context;

    boolean applicableFound = false;
    for (LanguageInjectionSupport support : InjectorUtils.getActiveInjectionSupports()) {
      if (!support.isApplicableTo(host)) continue;
      if (support == myInjectorSupport && applicableFound) continue;
      applicableFound = true;

      if (!support.useDefaultCommentInjector())
        continue;
      BaseInjection injection = support.findCommentInjection(host, null);
      if (injection == null) continue;
      if (!InjectorUtils.registerInjectionSimple(host, injection, support, registrar)) continue;
      return;
    }

    BaseInjection injection = !applicableFound ? myInjectorSupport.findCommentInjection(host, null) : null;
    if (injection == null) return;
    InjectorUtils.registerInjectionSimple(host, injection, myInjectorSupport, registrar);
  }
}
