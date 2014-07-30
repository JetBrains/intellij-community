/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.application;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * User: ksafonov
 */
public class ConfigImportSettings {

  public String getExecutableName() {
    return getProductName(ThreeState.NO);
  }

  public String[] getMainJarNames() {
    return new String[]{getProductName(ThreeState.NO)};
  }

  protected String getProductName(ThreeState full) {
    ApplicationNamesInfo namesInfo = ApplicationNamesInfo.getInstance();
    if (full == ThreeState.YES) {
      return namesInfo.getFullProductName();
    }
    else if (full == ThreeState.NO) {
      return namesInfo.getProductName();
    }
    else {
      return namesInfo.getProductName().equals("IDEA") ? namesInfo.getFullProductName() : namesInfo.getProductName();
    }
  }

  protected String getInaccessibleHomeErrorText(String instHome) {
    return ApplicationBundle.message("error.no.read.permissions", instHome);
  }

  protected String getInvalidHomeErrorText(String productWithVendor, String instHome) {
    return ApplicationBundle.message("error.does.not.appear.to.be.installation.home", instHome,
                                     productWithVendor);
  }

  protected String getCurrentHomeErrorText(String productWithVendor) {
    return ApplicationBundle.message("error.selected.current.installation.home",
                                     productWithVendor, productWithVendor);
  }

  protected String getEmptyHomeErrorText(String productWithVendor) {
    return ApplicationBundle.message("error.please.select.previous.installation.home", productWithVendor);
  }

  protected String getHomeLabel(String productName) {
    return ApplicationBundle.message("editbox.installation.home", productName);
  }

  protected String getAutoImportLabel(File guessedOldConfig) {
    String path = FileUtil.getLocationRelativeToUserHome(guessedOldConfig.getAbsolutePath());
    return ApplicationBundle.message("radio.import.auto", path);
  }

  protected String getDoNotImportLabel(String productName) {
    return ApplicationBundle.message("radio.do.not.import", productName);
  }

  protected String getTitleLabel(String productName) {
    return ApplicationBundle.message("label.you.can.import", productName);
  }

  @Nullable
  public String getCustomPathsSelector() {
    return null;
  }

  public String getInstallationHomeRequiredTitle() {
    return ApplicationBundle.message("title.installation.home.required");
  }

  public void importFinished(String newConfigPath) {
  }

  @NotNull
  public List<File> getCustomLaunchFilesCandidates(final File ideInstallationHome, 
                                                   final File ideBinFolder) {
    // custom files where to find config folder or path selector properties.
    // by default "Info.plist", "idea.properties"; "idea.sh,idea.bat,..." and 
    // "product_lower_name.sh, product_lower_name.bat,..." are used
    return Collections.emptyList();
  }
}
