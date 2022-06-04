// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.ui.SimpleColoredText;
import com.intellij.util.Consumer;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides host-language specific ways to configure language injections to some host-specific places
 * by configuring injection patterns in {@link org.intellij.plugins.intelliLang.InjectionsSettingsUI UI}
 * and saving them in {@link Configuration}
 *
 * @author Gregory.Shrago
 * @see DefaultLanguageInjector
 * @see Configuration
 */
public abstract class LanguageInjectionSupport {
  public static final ExtensionPointName<LanguageInjectionSupport> EP_NAME = ExtensionPointName.create("org.intellij.intelliLang.languageSupport");
  public static final ExtensionPointName<LanguageInjectionConfigBean> CONFIG_EP_NAME = ExtensionPointName.create("org.intellij.intelliLang.injectionConfig");

  public static final Key<InjectedLanguage> TEMPORARY_INJECTED_LANGUAGE = Key.create("TEMPORARY_INJECTED_LANGUAGE");
  public static final Key<LanguageInjectionSupport> INJECTOR_SUPPORT = Key.create("INJECTOR_SUPPORT");
  public static final Key<LanguageInjectionSupport> SETTINGS_EDITOR = Key.create("SETTINGS_EDITOR");

  /**
   * User visible Support ID name, usually is equal to the host language
   */
  public abstract @NlsSafe @NotNull String getId();

  /**
   * @return classes which have methods, that returns {@link com.intellij.patterns.ElementPattern}.
   * These methods will be used by reflection to build injection places patterns which will be stored in
   * <a href="https://www.jetbrains.com/help/idea/language-injection-settings-generic-javascript.html">settings</a>
   */
  public abstract Class<?> @NotNull [] getPatternClasses();

  /**
   * @return {@code true} if current LanguageInjectionSupport could handle the given {@code host}.
   * Usually it should be done by checking that the given {@code host} belongs to the current host-language
   */
  public abstract boolean isApplicableTo(PsiLanguageInjectionHost host);

  /**
   * @return {@code true} if {@link DefaultLanguageInjector} should be used to perform the injection configured for this support,
   * or {@code false} if there is another {@link MultiHostInjector} or better a
   * {@link com.intellij.lang.injection.general.LanguageInjectionPerformer LanguageInjectionPerformer}
   * implementation that does it for current LanguageInjectionSupport
   */
  public abstract boolean useDefaultInjector(PsiLanguageInjectionHost host);

  /**
   * @deprecated implement the {@link com.intellij.lang.injection.general.LanguageInjectionPerformer LanguageInjectionPerformer}
   * for your language instead of overriding this method.
   *
   * Returning {@code false} will make the {@link CommentLanguageInjector} not handle this {@link LanguageInjectionSupport},
   * but it is better to handle comment-based injection in the language specific
   * {@link com.intellij.lang.injection.general.LanguageInjectionContributor LanguageInjectionContributor}
   * and not deal with the {@link LanguageInjectionSupport} at all
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated(forRemoval = true)
  public abstract boolean useDefaultCommentInjector();

  /**
   * @deprecated implement the {@link com.intellij.lang.injection.general.LanguageInjectionContributor LanguageInjectionContributor}
   * for your language instead of implementing this method
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated(forRemoval = true)
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
