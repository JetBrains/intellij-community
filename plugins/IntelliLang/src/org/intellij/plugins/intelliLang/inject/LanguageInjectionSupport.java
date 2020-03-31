// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.ui.SimpleColoredText;
import com.intellij.util.Consumer;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Gregory.Shrago
 */
public abstract class LanguageInjectionSupport {
  public static final ExtensionPointName<LanguageInjectionSupport> EP_NAME = ExtensionPointName.create("org.intellij.intelliLang.languageSupport");
  public static final ExtensionPointName<LanguageInjectionConfigBean> CONFIG_EP_NAME = ExtensionPointName.create("org.intellij.intelliLang.injectionConfig");

  public static final Key<InjectedLanguage> TEMPORARY_INJECTED_LANGUAGE = Key.create("TEMPORARY_INJECTED_LANGUAGE");
  public static final Key<LanguageInjectionSupport> INJECTOR_SUPPORT = Key.create("INJECTOR_SUPPORT");
  public static final Key<LanguageInjectionSupport> SETTINGS_EDITOR = Key.create("SETTINGS_EDITOR");

  @NonNls
  @NotNull
  public abstract String getId();

  public abstract Class<?> @NotNull [] getPatternClasses();

  public abstract boolean isApplicableTo(PsiLanguageInjectionHost host);

  public abstract boolean useDefaultInjector(PsiLanguageInjectionHost host);

  public abstract boolean useDefaultCommentInjector();

  @Nullable
  public abstract BaseInjection findCommentInjection(@NotNull PsiElement host, @Nullable Ref<? super PsiElement> commentRef);

  public abstract boolean addInjectionInPlace(final Language language, final PsiLanguageInjectionHost psiElement);

  public abstract boolean removeInjectionInPlace(final PsiLanguageInjectionHost psiElement);

  public boolean removeInjection(final PsiElement psiElement) {
    return psiElement instanceof PsiLanguageInjectionHost && removeInjectionInPlace((PsiLanguageInjectionHost)psiElement);
  }

  public abstract boolean editInjectionInPlace(final PsiLanguageInjectionHost psiElement);

  public abstract BaseInjection createInjection(final Element element);

  public abstract void setupPresentation(final BaseInjection injection, final SimpleColoredText presentation, final boolean isSelected);

  public abstract Configurable[] createSettings(final Project project, final Configuration configuration);

  public abstract AnAction[] createAddActions(final Project project, final Consumer<? super BaseInjection> consumer);

  public abstract AnAction createEditAction(final Project project, final Factory<? extends BaseInjection> producer);
}
