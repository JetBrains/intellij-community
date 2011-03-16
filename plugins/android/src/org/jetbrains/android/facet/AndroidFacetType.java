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
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.newProject.AndroidModuleType;
import org.jetbrains.android.sdk.AndroidPlatform;
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

  private static boolean tryToSetAndroidPlatform(AndroidFacetConfiguration configuration, Library library) {
    AndroidPlatform platform = AndroidPlatform.parse(library, null, null);
    if (platform != null) {
      configuration.setAndroidPlatform(platform);
      return true;
    }
    return false;
  }

  public AndroidFacet createFacet(@NotNull Module module,
                                  String name,
                                  @NotNull AndroidFacetConfiguration configuration,
                                  @Nullable Facet underlyingFacet) {
    if (configuration.PLATFORM_NAME == null || configuration.PLATFORM_NAME.length() == 0) {
      setupPlatform(module, configuration);
    }

    return new AndroidFacet(module, name, configuration);
  }

  private static void setupPlatform(@NotNull Module module, @NotNull AndroidFacetConfiguration configuration) {
    for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        LibraryOrderEntry libEntry = (LibraryOrderEntry)entry;
        Library library = libEntry.getLibrary();
        if (library != null &&
            LibraryTablesRegistrar.APPLICATION_LEVEL.equals(libEntry.getLibraryLevel()) &&
            tryToSetAndroidPlatform(configuration, library)) {
            return;
        }
      }
    }

    PropertiesComponent component = PropertiesComponent.getInstance();
    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    if (component.isValueSet(AndroidSdkUtils.DEFAULT_PLATFORM_NAME_PROPERTY)) {
      Library defaultLib = libraryTable.getLibraryByName(component.getValue(AndroidSdkUtils.DEFAULT_PLATFORM_NAME_PROPERTY));
      if (defaultLib != null && tryToSetAndroidPlatform(configuration, defaultLib)) {
        return;
      }
      for (Library library : libraryTable.getLibraries()) {
        if (tryToSetAndroidPlatform(configuration, library)) {
          component.setValue(AndroidSdkUtils.DEFAULT_PLATFORM_NAME_PROPERTY, library.getName());
          return;
        }
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
