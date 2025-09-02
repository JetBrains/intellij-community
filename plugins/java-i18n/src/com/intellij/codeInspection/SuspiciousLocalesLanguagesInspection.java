// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.ResourceBundleImpl;
import com.intellij.lang.properties.customizeActions.DissociateResourceBundleAction;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.function.Supplier;

import static com.intellij.codeInspection.options.OptPane.pane;

public final class SuspiciousLocalesLanguagesInspection extends LocalInspectionTool {
  private static final String ADDITIONAL_LANGUAGES_ATTR_NAME = "additionalLanguages";
  private static final Supplier<Set<String>> JAVA_LOCALES = NotNullLazyValue.softLazy(() -> {
    final Set<String> result = new HashSet<>();
    for (Locale locale : Locale.getAvailableLocales()) {
      result.add(locale.getLanguage());
    }
    return result;
  });

  private final List<String> myAdditionalLanguages = new ArrayList<>();

  @TestOnly
  public void setAdditionalLanguages(List<String> additionalLanguages) {
    myAdditionalLanguages.clear();
    myAdditionalLanguages.addAll(additionalLanguages);
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    final String rawLanguages = node.getAttributeValue(ADDITIONAL_LANGUAGES_ATTR_NAME);
    if (rawLanguages != null) {
      myAdditionalLanguages.clear();
      myAdditionalLanguages.addAll(StringUtil.split(rawLanguages, ","));
    }
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (!myAdditionalLanguages.isEmpty()) {
      List<String> uniqueLanguages = ContainerUtil.sorted(new HashSet<>(myAdditionalLanguages));
      final String locales = StringUtil.join(uniqueLanguages, ",");
      node.setAttribute(ADDITIONAL_LANGUAGES_ATTR_NAME, locales);
    }
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(OptPane.stringList("myAdditionalLanguages", JavaI18nBundle.message("dissociate.resource.bundle.quick.fix.options.label")));
  }

  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
    if (propertiesFile == null) {
      return null;
    }
    final ResourceBundle resourceBundle = propertiesFile.getResourceBundle();
    final List<PropertiesFile> files = resourceBundle.getPropertiesFiles();
    if (!(resourceBundle instanceof ResourceBundleImpl) || files.size() < 2) {
      return null;
    }
    List<Locale> bundleLocales = ContainerUtil.mapNotNull(files, propertiesFile1 -> {
      final Locale locale = propertiesFile1.getLocale();
      return locale == PropertiesUtil.DEFAULT_LOCALE ? null : locale;
    });
    bundleLocales = ContainerUtil.filter(bundleLocales, locale -> !JAVA_LOCALES.get().contains(locale.getLanguage()) && !myAdditionalLanguages.contains(locale.getLanguage()));
    if (bundleLocales.isEmpty()) {
      return null;
    }
    final ProblemDescriptor descriptor = manager.createProblemDescriptor(file,
                                                                         JavaI18nBundle.message(
                                                                           "resource.bundle.contains.locales.with.suspicious.locale.languages.desciptor"),
                                                                         new DissociateResourceBundleQuickFix(resourceBundle),
                                                                         ProblemHighlightType.WEAK_WARNING,
                                                                         true);
    return new ProblemDescriptor[] {descriptor};
  }

  private static final class DissociateResourceBundleQuickFix implements LocalQuickFix {
    private final ResourceBundle myResourceBundle;

    private DissociateResourceBundleQuickFix(ResourceBundle bundle) {
      myResourceBundle = bundle;
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaI18nBundle.message("dissociate.resource.bundle.quick.fix.name");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      DissociateResourceBundleAction.dissociate(Collections.singleton(myResourceBundle), project);
    }
  }
}
