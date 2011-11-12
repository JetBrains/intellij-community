/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.android.facet;

import com.android.sdklib.SdkConstants;
import com.intellij.facet.FacetType;
import com.intellij.framework.detection.FacetBasedFrameworkDetector;
import com.intellij.framework.detection.FileContentPattern;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.patterns.ElementPattern;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.importDependencies.ImportDependenciesUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class AndroidFrameworkDetector extends FacetBasedFrameworkDetector<AndroidFacet, AndroidFacetConfiguration> {
  public AndroidFrameworkDetector() {
    super("android");
  }

  @Override
  public void setupFacet(@NotNull final AndroidFacet facet, final ModifiableRootModel model) {
    final Module module = facet.getModule();
    final Project project = module.getProject();

    ImportDependenciesUtil.importDependencies(module, true);

    StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
      public void run() {
        final Module module = facet.getModule();
        AndroidSdkUtils.setupAndroidPlatformInNeccessary(module);

        if (model != null && !model.isDisposed() && model.isWritable()) {
          model.setSdk(ModuleRootManager.getInstance(module).getSdk());
        }

        final String androidLibraryPropValue = AndroidUtils.getProjectPropertyValue(module, AndroidUtils.ANDROID_LIBRARY_PROPERTY);

        if (androidLibraryPropValue != null && androidLibraryPropValue.equals("true")) {
          facet.getConfiguration().LIBRARY_PROJECT = true;
        }
        else {
          Manifest manifest = facet.getManifest();
          if (manifest != null) {
            if (AndroidUtils.getDefaultActivityName(manifest) != null) {
              AndroidUtils.addRunConfiguration(project, facet, null, true, null, null);
            }
          }
        }

        ApplicationManager.getApplication().saveAll();
      }
    });
  }

  @Override
  public FacetType<AndroidFacet, AndroidFacetConfiguration> getFacetType() {
    return AndroidFacet.getFacetType();
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return StdFileTypes.XML;
  }

  @NotNull
  @Override
  public ElementPattern<FileContent> createSuitableFilePattern() {
    return FileContentPattern.fileContent().withName(SdkConstants.FN_ANDROID_MANIFEST_XML);
  }
}
