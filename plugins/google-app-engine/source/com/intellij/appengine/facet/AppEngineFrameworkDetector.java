/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.appengine.facet;

import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.sdk.AppEngineSdkManager;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.facet.FacetType;
import com.intellij.framework.detection.FacetBasedFrameworkDetector;
import com.intellij.framework.detection.FileContentPattern;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.patterns.ElementPattern;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public class AppEngineFrameworkDetector extends FacetBasedFrameworkDetector<AppEngineFacet,  AppEngineFacetConfiguration> {
  public AppEngineFrameworkDetector() {
    super("appengine-java");
  }

  @Override
  public FacetType<AppEngineFacet, AppEngineFacetConfiguration> getFacetType() {
    return FacetType.findInstance(AppEngineFacetType.class);
  }

  @Override
  public void setupFacet(@NotNull AppEngineFacet facet, ModifiableRootModel model) {
    final List<? extends AppEngineSdk> sdks = AppEngineSdkManager.getInstance().getValidSdks();
    if (!sdks.isEmpty()) {
      facet.getConfiguration().setSdkHomePath(sdks.get(0).getSdkHomePath());
    }
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return StdFileTypes.XML;
  }

  @NotNull
  @Override
  public ElementPattern<FileContent> createSuitableFilePattern() {
    return FileContentPattern.fileContent().withName(AppEngineUtil.APP_ENGINE_WEB_XML_NAME).xmlWithRootTag("appengine-web-app");
  }
}
