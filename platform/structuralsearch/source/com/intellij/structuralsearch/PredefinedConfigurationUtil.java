// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PredefinedConfigurationUtil {

  /**
   * @deprecated this creates a Java template, which is most likely not what you need. Use
   * {@link #createSearchTemplateInfo(java.lang.String, java.lang.String, java.lang.String, com.intellij.openapi.fileTypes.LanguageFileType)}
   * instead.
   */
  @Deprecated
  @NotNull
  public static Configuration createSearchTemplateInfo(@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String name,
                                                       @NonNls @NotNull String criteria, @NotNull String category) {
    return createSearchTemplateInfo(name, criteria, category, StdFileTypes.JAVA);
  }

  @NotNull
  public static Configuration createSearchTemplateInfo(@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String name,
                                                       @NonNls @NotNull String criteria,
                                                       @NotNull String category, @NotNull LanguageFileType fileType) {
    return createSearchTemplateInfo(name, criteria, category, fileType, null);
  }

  @NotNull
  public static Configuration createSearchTemplateInfo(@NotNull String name,
                                                       @NonNls @NotNull String criteria,
                                                       @NotNull String category,
                                                       @NotNull LanguageFileType fileType,
                                                       @Nullable PatternContext context) {
    final SearchConfiguration config = new SearchConfiguration(name, category);
    config.setPredefined(true);

    final MatchOptions options = config.getMatchOptions();
    options.fillSearchCriteria(criteria);
    options.setFileType(fileType);
    options.setCaseSensitiveMatch(true);
    options.setPatternContext(context);

    return config;
  }

  /**
   * @deprecated this creates a Java template, which is most likely not what you need.
   */
  @Deprecated
  public static Configuration createSearchTemplateInfoSimple(@Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                                             @NonNls String criteria, String category) {
    final Configuration info = createSearchTemplateInfo(name, criteria, category);
    info.getMatchOptions().setRecursiveSearch(false);

    return info;
  }
}
