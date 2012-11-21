/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.uipreview;

import com.android.SdkConstants;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.api.IProjectCallback;
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.FrameworkResources;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.sdk.LoadStatus;
import com.android.io.IAbstractFolder;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.utils.ILogger;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.BufferingFileWrapper;
import org.jetbrains.android.util.BufferingFolderWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class RenderServiceFactory {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.RenderServiceFactory");

  private final Map<String,Map<String,Integer>> myEnumMap;

  private LayoutLibrary myLibrary;
  private FrameworkResources myResources;

  public LayoutLibrary getLibrary() {
    return myLibrary;
  }

  @Nullable
  public static RenderServiceFactory create(@NotNull IAndroidTarget target,
                                            @NotNull Map<String, Map<String, Integer>> enumMap) throws RenderingException, IOException {
    final RenderServiceFactory factory = new RenderServiceFactory(enumMap);
    if (factory.loadLibrary(target)) {
      return factory;
    }
    return null;
  }

  public Pair<RenderResources, RenderResources> createResourceResolver(final AndroidFacet facet,
                                                                       FolderConfiguration config,
                                                                       ProjectResources projectResources,
                                                                       String themeName,
                                                                       boolean isProjectTheme) {
    final Map<ResourceType, Map<String, ResourceValue>> configedProjectRes = projectResources.getConfiguredResources(config);

    DumbService.getInstance(facet.getModule().getProject()).waitForSmartMode();

    final Collection<String> ids = ApplicationManager.getApplication().runReadAction(new Computable<Collection<String>>() {
      @Override
      public Collection<String> compute() {
        return facet.getLocalResourceManager().getIds();
      }
    });
    final Map<String, ResourceValue> map = configedProjectRes.get(ResourceType.ID);
    for (String id : ids) {
      if (!map.containsKey(id)) {
        map.put(id, new ResourceValue(ResourceType.ID, id, false));
      }
    }

    final Map<ResourceType, Map<String, ResourceValue>> configedFrameworkRes = myResources.getConfiguredResources(config);
    final ResourceResolver resolver = ResourceResolver.create(configedProjectRes, configedFrameworkRes, themeName, isProjectTheme);
    return new Pair<RenderResources, RenderResources>(new ResourceResolverDecorator(resolver), resolver);
  }

  public RenderService createService(RenderResources resources,
                                     RenderResources legacyResources,
                                     FolderConfiguration config,
                                     double xdpi,
                                     double ydpi,
                                     IProjectCallback projectCallback,
                                     int minSdkVersion) {
    return new RenderService(myLibrary, resources, legacyResources, config, xdpi, ydpi, projectCallback, minSdkVersion);
  }

  private RenderServiceFactory(@NotNull Map<String, Map<String, Integer>> enumMap) {
    myEnumMap = enumMap;
  }

  private boolean loadLibrary(@NotNull IAndroidTarget target) throws RenderingException, IOException {
    final String layoutLibJarPath = target.getPath(IAndroidTarget.LAYOUT_LIB);
    final VirtualFile layoutLibJar = LocalFileSystem.getInstance().findFileByPath(layoutLibJarPath);
    if (layoutLibJar == null || layoutLibJar.isDirectory()) {
      throw new RenderingException(AndroidBundle.message("android.file.not.exist.error", FileUtil.toSystemDependentName(layoutLibJarPath)));
    }

    final String resFolderPath = target.getPath(IAndroidTarget.RESOURCES);
    final VirtualFile resFolder = LocalFileSystem.getInstance().findFileByPath(resFolderPath);
    if (resFolder == null || !resFolder.isDirectory()) {
      throw new RenderingException(
        AndroidBundle.message("android.directory.cannot.be.found.error", FileUtil.toSystemDependentName(resFolderPath)));
    }

    final String fontFolderPath = target.getPath(IAndroidTarget.FONTS);
    final VirtualFile fontFolder = LocalFileSystem.getInstance().findFileByPath(fontFolderPath);
    if (fontFolder == null || !fontFolder.isDirectory()) {
      throw new RenderingException(
        AndroidBundle.message("android.directory.cannot.be.found.error", FileUtil.toSystemDependentName(fontFolderPath)));
    }

    final String platformFolderPath = target.isPlatform() ? target.getLocation() : target.getParent().getLocation();
    final File platformFolder = new File(platformFolderPath);
    if (!platformFolder.isDirectory()) {
      throw new RenderingException(
        AndroidBundle.message("android.directory.cannot.be.found.error", FileUtil.toSystemDependentName(platformFolderPath)));
    }

    final File buildProp = new File(platformFolder, SdkConstants.FN_BUILD_PROP);
    if (!buildProp.isFile()) {
      throw new RenderingException(
        AndroidBundle.message("android.file.not.exist.error", FileUtil.toSystemDependentName(buildProp.getPath())));
    }

    final SimpleLogger logger = new SimpleLogger(null, LOG);

    myLibrary = LayoutLibrary.load(layoutLibJar.getPath(), logger, ApplicationNamesInfo.getInstance().getFullProductName());
    if (myLibrary.getStatus() != LoadStatus.LOADED) {
      throw new RenderingException(myLibrary.getLoadMessage());
    }

    myResources = loadPlatformResources(new File(resFolder.getPath()), logger);

    final Map<String, String> buildPropMap = ProjectProperties.parsePropertyFile(new BufferingFileWrapper(buildProp), logger);
    return myLibrary.init(buildPropMap, new File(fontFolder.getPath()), myEnumMap, logger);
  }

  private static FrameworkResources loadPlatformResources(File resFolder, ILogger log) throws IOException, RenderingException {
    final IAbstractFolder resFolderWrapper = new BufferingFolderWrapper(resFolder);
    final FrameworkResources resources = new FrameworkResources(resFolderWrapper);

    RenderUtil.loadResources(resources, null, null, resFolderWrapper);

    resources.loadPublicResources(log);
    return resources;
  }
}
