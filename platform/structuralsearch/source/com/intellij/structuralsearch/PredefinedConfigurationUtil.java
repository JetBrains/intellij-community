// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;
import org.jetbrains.annotations.NonNls;

public class PredefinedConfigurationUtil {

  public static Configuration createSearchTemplateInfo(String name, @NonNls String criteria, String category) {
    return createSearchTemplateInfo(name, criteria, category, StdFileTypes.JAVA);
  }

  public static Configuration createSearchTemplateInfo(String name, @NonNls String criteria, String category, FileType fileType) {
    final SearchConfiguration config = new SearchConfiguration(name, category);
    config.setPredefined(true);
    final MatchOptions options = config.getMatchOptions();
    options.fillSearchCriteria(criteria);
    options.setFileType(fileType);
    options.setCaseSensitiveMatch(true);

    return config;
  }

  public static Configuration createSearchTemplateInfoSimple(String name, @NonNls String criteria, String category) {
    final Configuration info = createSearchTemplateInfo(name,criteria,category);
    info.getMatchOptions().setRecursiveSearch(false);

    return info;
  }
}
