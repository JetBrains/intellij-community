/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.usages;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NonNls;

import java.io.File;

@State(
  name = "UsageViewSettings",
  storages = {
    @Storage("usageView.xml"),
    @Storage(value = "other.xml", deprecated = true)
  }
)
public class UsageViewSettings implements PersistentStateComponent<UsageViewSettings> {
  @NonNls public String EXPORT_FILE_NAME = "report.txt";
  public boolean IS_EXPANDED = false;
  public boolean IS_SHOW_PACKAGES = true;
  public boolean IS_SHOW_METHODS = false;
  public boolean IS_AUTOSCROLL_TO_SOURCE = false;
  public boolean IS_FILTER_DUPLICATED_LINE = false;
  public boolean IS_SHOW_MODULES = false;
  public boolean IS_PREVIEW_USAGES = false;
  public boolean IS_SORT_MEMBERS_ALPHABETICALLY = true;
  public float PREVIEW_USAGES_SPLITTER_PROPORTIONS = 0.5f;

  public boolean GROUP_BY_USAGE_TYPE = true;
  public boolean GROUP_BY_MODULE = true;
  public boolean GROUP_BY_PACKAGE = true;
  public boolean GROUP_BY_FILE_STRUCTURE = true;
  public boolean GROUP_BY_SCOPE = false;

  public static UsageViewSettings getInstance() {
    return ServiceManager.getService(UsageViewSettings.class);
  }

  public boolean isExpanded() {
    return IS_EXPANDED;
  }

  public void setExpanded(boolean val) {
    IS_EXPANDED = val;
  }

  public boolean isShowPackages() {
    return IS_SHOW_PACKAGES;
  }

  public void setShowPackages(boolean val) {
    IS_SHOW_PACKAGES = val;
  }

  public boolean isShowMethods() {
    return IS_SHOW_METHODS;
  }

  public boolean isShowModules() {
    return IS_SHOW_MODULES;
  }

  public void setShowMethods(boolean val) {
    IS_SHOW_METHODS = val;
  }

  public void setShowModules(boolean val) {
    IS_SHOW_MODULES = val;
  }

  public boolean isFilterDuplicatedLine() {
    return IS_FILTER_DUPLICATED_LINE;
  }

  public void setFilterDuplicatedLine(boolean val) {
    IS_FILTER_DUPLICATED_LINE = val;
  }

  @Transient
  public String getExportFileName() {
    return EXPORT_FILE_NAME != null ? EXPORT_FILE_NAME.replace('/', File.separatorChar) : null;
  }

  public void setExportFileName(String s) {
    if (s != null){
      s = s.replace(File.separatorChar, '/');
    }
    EXPORT_FILE_NAME = s;
  }

  @Override
  public UsageViewSettings getState() {
    return this;
  }

  @Override
  public void loadState(final UsageViewSettings object) {
    XmlSerializerUtil.copyBean(object, this);
  }
}
