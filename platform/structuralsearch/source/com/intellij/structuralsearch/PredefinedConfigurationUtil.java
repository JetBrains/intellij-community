package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;
import org.jetbrains.annotations.NonNls;

public class PredefinedConfigurationUtil {

  public static Configuration createSearchTemplateInfo(String name, @NonNls String criteria, String category) {
    return createSearchTemplateInfo(name, criteria, category, StdFileTypes.JAVA);
  }

  public static Configuration createSearchTemplateInfo(String name, @NonNls String criteria, String category, FileType fileType) {
    final SearchConfiguration config = new SearchConfiguration();
    config.setPredefined(true);
    config.setName(name);
    config.setCategory(category);
    final MatchOptions options = config.getMatchOptions();
    options.setSearchPattern(criteria);
    options.setFileType(fileType);
    options.setCaseSensitiveMatch(true);
    MatcherImplUtil.transform(options);

    return config;
  }

  public static Configuration createSearchTemplateInfoSimple(String name, @NonNls String criteria, String category) {
    final Configuration info = createSearchTemplateInfo(name,criteria,category);
    info.getMatchOptions().setRecursiveSearch(false);

    return info;
  }
}
