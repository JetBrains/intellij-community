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

import com.android.ide.common.log.ILogger;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.api.IProjectCallback;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.FrameworkResources;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.*;
import com.android.ide.common.sdk.LoadStatus;
import com.android.io.FileWrapper;
import com.android.io.FolderWrapper;
import com.android.resources.*;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.internal.project.ProjectProperties;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class RenderServiceFactory {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.RenderServiceFactory");

  private final Map<String,Map<String,Integer>> myEnumMap;

  private LayoutLibrary myLibrary;
  private FrameworkResources myResources;

  @Nullable
  public static RenderServiceFactory create(@NotNull IAndroidTarget target,
                                            @NotNull Map<String, Map<String, Integer>> enumMap) throws RenderingException, IOException {
    final RenderServiceFactory factory = new RenderServiceFactory(enumMap);
    if (factory.loadLibrary(target)) {
      return factory;
    }
    return null;
  }

  public static FolderConfiguration createConfig(int size1,
                                                 int size2,
                                                 ScreenSize screenSize,
                                                 ScreenRatio screenRatio,
                                                 ScreenOrientation orientation,
                                                 Density density,
                                                 TouchScreen touchScreen,
                                                 KeyboardState keyboardState,
                                                 Keyboard keyboard,
                                                 NavigationState navigationState,
                                                 Navigation navigation) {
    final FolderConfiguration config = new FolderConfiguration();
    config.addQualifier(new ScreenSizeQualifier(screenSize));
    config.addQualifier(new ScreenRatioQualifier(screenRatio));
    config.addQualifier(new ScreenOrientationQualifier(orientation));
    config.addQualifier(new PixelDensityQualifier(density));
    config.addQualifier(new TouchScreenQualifier(touchScreen));
    config.addQualifier(new KeyboardStateQualifier(keyboardState));
    config.addQualifier(new TextInputMethodQualifier(keyboard));
    config.addQualifier(new NavigationStateQualifier(navigationState));
    config.addQualifier(new NavigationMethodQualifier(navigation));
    config.addQualifier(new ScreenDimensionQualifier(size1, size2));
    return config;
  }

  public ResourceResolver createResourceResolver(FolderConfiguration config,
                                                 ResourceRepository projectResources,
                                                 String themeName,
                                                 boolean isProjectTheme) {
    final Map<ResourceType, Map<String, ResourceValue>> configedProjectRes = projectResources.getConfiguredResources(config);
    final Map<ResourceType, Map<String, ResourceValue>> configedFrameworkRes = myResources.getConfiguredResources(config);
    return ResourceResolver.create(configedProjectRes, configedFrameworkRes, themeName, isProjectTheme);
  }

  public RenderService createService(ResourceResolver resources,
                                     FolderConfiguration config,
                                     IProjectCallback projectCallback,
                                     int minSdkVersion) {
    return new RenderService(myLibrary, resources, config, projectCallback, minSdkVersion);
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

    final FileWrapper buildProp = new FileWrapper(platformFolder, SdkConstants.FN_BUILD_PROP);
    if (!buildProp.isFile()) {
      throw new RenderingException(
        AndroidBundle.message("android.file.not.exist.error", FileUtil.toSystemDependentName(buildProp.getPath())));
    }

    final SimpleLogger logger = new SimpleLogger(LOG);

    myLibrary = LayoutLibrary.load(layoutLibJar.getPath(), logger);
    if (myLibrary.getStatus() != LoadStatus.LOADED) {
      throw new RenderingException(myLibrary.getLoadMessage());
    }

    myResources = loadPlatformResources(new File(resFolder.getPath()), logger);

    final Map<String, String> buildPropMap = ProjectProperties.parsePropertyFile(buildProp, logger);
    return myLibrary.init(buildPropMap, new File(fontFolder.getPath()), myEnumMap, logger);
  }

  private static FrameworkResources loadPlatformResources(File resFolder, ILogger log) throws IOException {
    final FrameworkResources resources = new FrameworkResources();
    final FolderWrapper resFolderWrapper = new FolderWrapper(resFolder);
    RenderUtil.loadResources(resources, resFolderWrapper);
    resources.loadPublicResources(resFolderWrapper, log);
    return resources;
  }
}
