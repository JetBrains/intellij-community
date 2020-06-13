// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;
import org.jetbrains.annotations.NonNls;

public final class PredefinedConfigurationUtil {

  /**
   * @deprecated this creates a Java template, which is most likely not what you need. Use
   * {@link #createSearchTemplateInfo(java.lang.String, java.lang.String, java.lang.String, com.intellij.openapi.fileTypes.LanguageFileType)}
   * instead.
   */
  @Deprecated
  public static Configuration createSearchTemplateInfo(String name, @NonNls String criteria, String category) {
    return createSearchTemplateInfo(name, criteria, category, StdFileTypes.JAVA);
  }

  public static Configuration createSearchTemplateInfo(String name, @NonNls String criteria, String category, LanguageFileType fileType) {
    return createSearchTemplateInfo(name, criteria, category, fileType, null);
  }

  public static Configuration createSearchTemplateInfo(String name,
                                                       @NonNls String criteria,
                                                       String category,
                                                       LanguageFileType fileType,
                                                       PatternContext context) {
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
  public static Configuration createSearchTemplateInfoSimple(String name, @NonNls String criteria, String category) {
    final Configuration info = createSearchTemplateInfo(name,criteria,category);
    info.getMatchOptions().setRecursiveSearch(false);

    return info;
  }
}
