/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    final SearchConfiguration config = new SearchConfiguration();
    config.setPredefined(true);
    config.setName(name);
    config.setCategory(category);
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
