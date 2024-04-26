// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.breadcrumbs;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiManager;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.ui.breadcrumbs.BreadcrumbsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BreadcrumbsUtilEx {
  static @Nullable FileViewProvider findViewProvider(final VirtualFile file, final Project project) {
    if (file == null || file.isDirectory()) return null;
    return PsiManager.getInstance(project).findViewProvider(file);
  }

  static @Nullable BreadcrumbsProvider findProvider(VirtualFile file, @Nullable Project project, @Nullable Boolean forcedShown) {
    return project == null ? null : findProvider(findViewProvider(file, project), forcedShown);
  }

  public static @Nullable BreadcrumbsProvider findProvider(@Nullable FileViewProvider viewProvider, @Nullable Boolean forceShown) {
    if (viewProvider == null) return null;

    if (forceShown == null) {
      return findProvider(true, viewProvider);
    }
    return forceShown ? findProvider(false, viewProvider) : null;
  }

  public static @Nullable BreadcrumbsProvider findProvider(boolean checkSettings, @NotNull FileViewProvider viewProvider) {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (checkSettings && !settings.isBreadcrumbsShown()) return null;

    Language baseLang = viewProvider.getBaseLanguage();
    if (checkSettings && !isBreadcrumbsShownFor(baseLang)) return null;

    BreadcrumbsProvider provider = BreadcrumbsUtil.getInfoProvider(baseLang);
    if (provider == null) {
      for (Language language : viewProvider.getLanguages()) {
        if (!checkSettings || isBreadcrumbsShownFor(language)) {
          provider = BreadcrumbsUtil.getInfoProvider(language);
          if (provider != null) break;
        }
      }
    }
    return provider;
  }

  public static boolean isBreadcrumbsShownFor(Language language) {
    String id = findLanguageWithBreadcrumbSettings(language);
    return EditorSettingsExternalizable.getInstance().isBreadcrumbsShownFor(id);
  }

  public static String findLanguageWithBreadcrumbSettings(Language language) {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    Language base = language;
    while (base != null) {
      if (settings.hasBreadcrumbSettings(base.getID())) {
        return base.getID();
      }
      base = base.getBaseLanguage();
    }
    return language.getID();
  }
}
