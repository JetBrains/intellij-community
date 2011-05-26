/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.autodetecting.FacetDetectorRegistry;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.newProject.AndroidModuleType;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author yole
 */
public class AndroidFacetType extends FacetType<AndroidFacet, AndroidFacetConfiguration> {

  public AndroidFacetType() {
    super(AndroidFacet.ID, "android", "Android");
  }


  public AndroidFacetConfiguration createDefaultConfiguration() {
    return new AndroidFacetConfiguration();
  }

  private static boolean tryToSetAndroidPlatform(Module module, Sdk sdk) {
    AndroidPlatform platform = AndroidPlatform.parse(sdk);
    if (platform != null) {
      setSdk(module, sdk);
      return true;
    }
    return false;
  }

  private static void setSdk(Module module, Sdk sdk) {
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    model.setSdk(sdk);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        model.commit();
      }
    });
  }

  public AndroidFacet createFacet(@NotNull Module module,
                                  String name,
                                  @NotNull AndroidFacetConfiguration configuration,
                                  @Nullable Facet underlyingFacet) {
    // DO NOT COMMIT MODULE-ROOT MODELS HERE!
    // modules are not initialized yet, so some data may be lost

    return new AndroidFacet(module, name, configuration);
  }

  private static void setupAndroidPlatformInNeccessary(Module module) {
    Sdk currentSdk = ModuleRootManager.getInstance(module).getSdk();
    if (currentSdk == null || !(currentSdk.getSdkType().equals(AndroidSdkType.getInstance()))) {
      setupPlatform(module);
    }
  }

  private static void setupPlatform(@NotNull Module module) {
    PropertiesComponent component = PropertiesComponent.getInstance();
    if (component.isValueSet(AndroidSdkUtils.DEFAULT_PLATFORM_NAME_PROPERTY)) {
      String defaultPlatformName = component.getValue(AndroidSdkUtils.DEFAULT_PLATFORM_NAME_PROPERTY);
      Sdk defaultLib = ProjectJdkTable.getInstance().findJdk(defaultPlatformName, AndroidSdkType.getInstance().getName());
      if (defaultLib != null && tryToSetAndroidPlatform(module, defaultLib)) {
        return;
      }
    }
    for (Sdk sdk : ProjectJdkTable.getInstance().getSdksOfType(AndroidSdkType.getInstance())) {
      if (tryToSetAndroidPlatform(module, sdk)) {
        component.setValue(AndroidSdkUtils.DEFAULT_PLATFORM_NAME_PROPERTY, sdk.getName());
        return;
      }
    }
  }

  public boolean isSuitableModuleType(ModuleType moduleType) {
    return moduleType instanceof JavaModuleType || moduleType instanceof AndroidModuleType;
  }

  public void registerDetectors(FacetDetectorRegistry<AndroidFacetConfiguration> detectorRegistry) {
    FacetDetector<VirtualFile, AndroidFacetConfiguration> detector = new FacetDetector<VirtualFile, AndroidFacetConfiguration>() {
      public AndroidFacetConfiguration detectFacet(VirtualFile source, Collection<AndroidFacetConfiguration> existentFacetConfigurations) {
        if (!existentFacetConfigurations.isEmpty()) {
          return existentFacetConfigurations.iterator().next();
        }
        return createDefaultConfiguration();
      }

      @Override
      public void afterFacetAdded(@NotNull final Facet facet) {
        if (facet instanceof AndroidFacet) {
          final Project project = facet.getModule().getProject();
          StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
            public void run() {
              setupAndroidPlatformInNeccessary(facet.getModule());

              final AndroidFacet androidFacet = (AndroidFacet)facet;
              Manifest manifest = androidFacet.getManifest();
              if (manifest != null) {
                if (AndroidUtils.getDefaultActivityName(manifest) != null) {
                  AndroidUtils.addRunConfiguration(project, androidFacet, null, true);
                }
              }
              ApplicationManager.getApplication().saveAll();
            }
          });
        }
      }
    };
    VirtualFileFilter androidManifestFilter = new VirtualFileFilter() {
      public boolean accept(VirtualFile file) {
        return file.getName().equals(SdkConstants.FN_ANDROID_MANIFEST_XML);
      }
    };
    detectorRegistry.registerUniversalDetector(StdFileTypes.XML, androidManifestFilter, detector);
  }

  public Icon getIcon() {
    return AndroidUtils.ANDROID_ICON;
  }
}
