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

import com.android.SdkConstants;
import com.intellij.facet.FacetType;
import com.intellij.framework.detection.FacetBasedFrameworkDetector;
import com.intellij.framework.detection.FileContentPattern;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.ElementPattern;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.importDependencies.ImportDependenciesUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
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
        AndroidSdkUtils.setupAndroidPlatformInNecessary(module, true);

        if (model != null && !model.isDisposed() && model.isWritable()) {
          model.setSdk(ModuleRootManager.getInstance(module).getSdk());
        }

        final Pair<String,VirtualFile> manifestMergerProp =
          AndroidRootUtil.getProjectPropertyValue(module, AndroidCommonUtils.ANDROID_MANIFEST_MERGER_PROPERTY);
        if (manifestMergerProp != null && Boolean.parseBoolean(manifestMergerProp.getFirst())) {
          Notifications.Bus.notify(new Notification("Android", "Error importing module " + module.getName(),
                                                    AndroidBundle.message("android.manifest.merger.not.supported.error"),
                                                    NotificationType.ERROR));
        }
        final Pair<String, VirtualFile> androidLibraryProp =
          AndroidRootUtil.getProjectPropertyValue(module, AndroidUtils.ANDROID_LIBRARY_PROPERTY);

        if (androidLibraryProp != null && Boolean.parseBoolean(androidLibraryProp.getFirst())) {
          facet.getConfiguration().LIBRARY_PROJECT = true;
        }
        else {
          Manifest manifest = facet.getManifest();
          if (manifest != null) {
            if (AndroidUtils.getDefaultActivityName(manifest) != null) {
              AndroidUtils.addRunConfiguration(facet, null, false, null, null);
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
