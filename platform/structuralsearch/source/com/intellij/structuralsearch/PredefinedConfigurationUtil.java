// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;
import org.jetbrains.annotations.*;

public final class PredefinedConfigurationUtil {

  /**
   * @deprecated this creates a Java template, which is most likely not what you need. Use
   * {@link #createSearchTemplateInfo(String, String, String, LanguageFileType)}
   * instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  @NotNull
  public static Configuration createSearchTemplateInfo(@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String name,
                                                       @NonNls @NotNull String criteria, @NotNull String category) {
    return createSearchTemplateInfo(name, criteria, category, StdFileTypes.JAVA);
  }

  /**
   * @deprecated Predefined templates can be reference in other pattern by name, but their name can be translated.
   * Use {@link #createConfiguration(String, String, String, String, LanguageFileType, PatternContext)} instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  @NotNull
  public static Configuration createSearchTemplateInfo(@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String name,
                                                       @NonNls @NotNull String criteria,
                                                       @NotNull String category, @NotNull LanguageFileType fileType) {
    return createSearchTemplateInfo(name, criteria, category, fileType, null);
  }

  /**
   * @deprecated Predefined templates can be reference in other pattern by name, but their name can be translated.
   * Use {@link #createConfiguration(String, String, String, String, LanguageFileType, PatternContext)} instead.
   */
  @Deprecated
  @NotNull
  public static Configuration createSearchTemplateInfo(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String name,
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
    final Configuration config = createSearchTemplateInfo(name, criteria, category, fileType, context);
    config.setRefName(refName + " (" + config.getFileType().getLanguage().getDisplayName() + ")");
    return config;
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
    final Configuration config = createSearchTemplateInfo(name, criteria, category, fileType, context);
    config.setRefName(refName);
    return config;
  }

  /**
   * @deprecated this creates a Java template, which is most likely not what you need.
   * Use {@link #createNonRecursiveConfiguration(String, String, String, String, LanguageFileType, PatternContext)} instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  public static Configuration createSearchTemplateInfoSimple(@Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                                             @NonNls String criteria, String category) {
    final Configuration info = createSearchTemplateInfo(name, criteria, category);
    info.getMatchOptions().setRecursiveSearch(false);

    return info;
  }

  @NotNull
  public static Configuration createNonRecursiveConfiguration(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                                              @NotNull @NonNls String refName,
                                                              @NotNull @NonNls String criteria,
                                                              @NotNull String category,
                                                              @NotNull LanguageFileType fileType,
                                                              @Nullable PatternContext context) {
    final Configuration config = createConfiguration(name, refName, criteria, category, fileType, context);
    config.getMatchOptions().setRecursiveSearch(false);
    return config;
  }

  @NotNull
  public static Configuration createLegacyNonRecursiveConfiguration(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                                                    @NotNull @NonNls String refName,
                                                                    @NotNull @NonNls String criteria,
                                                                    @NotNull String category,
                                                                    @NotNull LanguageFileType fileType,
                                                                    @Nullable PatternContext context) {
    final Configuration config = createLegacyConfiguration(name, refName, criteria, category, fileType, context);
    config.getMatchOptions().setRecursiveSearch(false);
    return config;
  }
}
