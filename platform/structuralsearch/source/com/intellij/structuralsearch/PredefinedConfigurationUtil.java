// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;
import org.jetbrains.annotations.*;

public final class PredefinedConfigurationUtil {
  @NotNull
  public static Configuration createConfiguration(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                                  @NotNull @NonNls String refName,
                                                  @NotNull @NonNls String criteria,
                                                  @NotNull String category,
                                                  @NotNull LanguageFileType fileType) {
    return createConfiguration(name, refName, criteria, category, fileType, null);
  }

  @NotNull
  public static Configuration createLegacyConfiguration(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                                  @NotNull @NonNls String refName,
                                                  @NotNull @NonNls String criteria,
                                                  @NotNull String category,
                                                  @NotNull LanguageFileType fileType) {
    return createLegacyConfiguration(name, refName, criteria, category, fileType, null);
  }

  /**
   * This creates a predefined search configuration.
   * The language name will be added to the provided refName.
   * @param name localizable name of the template
   * @param refName unique template identifier (within the configurations of its language) used by the reference filter
   */
  @NotNull
  public static Configuration createConfiguration(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                                  @NotNull @NonNls String refName,
                                                  @NotNull @NonNls String criteria,
                                                  @NotNull String category,
                                                  @NotNull LanguageFileType fileType,
                                                  @Nullable PatternContext context) {
    return createLegacyConfiguration(name, refName + " (" + fileType.getLanguage().getDisplayName() + ")",
                                     criteria, category, fileType, context);
  }

  /**
   * This creates a predefined search configuration with backwards reference support.
   * If you are creating a new configuration, use
   * {@link #createConfiguration(String, String, String, String, LanguageFileType, PatternContext)} instead.
   */
  @NotNull
  public static Configuration createLegacyConfiguration(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                                  @NotNull @NonNls String refName,
                                                  @NotNull @NonNls String criteria,
                                                  @NotNull String category,
                                                  @NotNull LanguageFileType fileType,
                                                  @Nullable PatternContext context) {
    final SearchConfiguration config = new SearchConfiguration(name, category);
    config.setPredefined(true);
    config.setRefName(refName);

    final MatchOptions options = config.getMatchOptions();
    options.fillSearchCriteria(criteria);
    options.setFileType(fileType);
    options.setCaseSensitiveMatch(true);
    options.setPatternContext(context);

    return config;
  }
}
