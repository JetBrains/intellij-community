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

import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.api.*;
import com.android.ide.common.rendering.api.SessionParams.RenderingMode;
import com.android.ide.common.resources.configuration.*;
import com.android.resources.Density;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenSize;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.awt.*;
import java.io.FileNotFoundException;
import java.io.StringReader;


/**
 * @author Eugene.Kudelevsky
 */
class RenderService {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.RenderService");

  private final IProjectCallback myProjectCallback;
  private final RenderResources myResourceResolver;
  private final RenderResources myLegacyResourceResolver;
  private final LayoutLibrary myLayoutLib;
  private final FolderConfiguration myConfig;
  private final int myMinSdkVersion;
  private final float myXdpi;
  private final float myYdpi;

  private static final Object PROJECT_KEY = new Object();

  RenderService(LayoutLibrary layoutLibrary,
                @NotNull RenderResources resourceResolver,
                @NotNull RenderResources legacyResourceResolver,
                FolderConfiguration config,
                float xdpi,
                float ydpi,
                IProjectCallback projectCallback,
                int minSdkVersion) {
    myLayoutLib = layoutLibrary;
    myResourceResolver = resourceResolver;
    myLegacyResourceResolver = legacyResourceResolver;
    myConfig = config;
    myProjectCallback = projectCallback;
    myMinSdkVersion = minSdkVersion;
    myXdpi = xdpi;
    myYdpi = ydpi;
  }

  @Nullable
  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public RenderSession createRenderSession(@NotNull String layoutXmlText,
                                           @NotNull String appLabel,
                                           long timeout,
                                           @NotNull LayoutLog log)
    throws FileNotFoundException, XmlPullParserException {

    final ILayoutPullParser parser = new XmlParser();
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
    parser.setInput(new StringReader(layoutXmlText));

    final Dimension dimension = getDimension();
    if (dimension == null) {
      return null;
    }

    final VersionQualifier versionQualifier = myConfig.getVersionQualifier();
    if (versionQualifier == null) {
      return null;
    }

    final int targetSdkVersion = versionQualifier.getVersion();
    final int minSdkVersion = myMinSdkVersion >= 0 ? myMinSdkVersion : targetSdkVersion;

    final DensityQualifier densityQualifier = myConfig.getDensityQualifier();
    final Density density = densityQualifier != null ? densityQualifier.getValue() : Density.MEDIUM;
    final float xdpi = Float.isNaN(myXdpi) ? density.getDpiValue() : myXdpi;
    final float ydpi = Float.isNaN(myYdpi) ? density.getDpiValue() : myYdpi;

    final RenderResources resolver = myLayoutLib.getRevision() > 0 ? myResourceResolver : myLegacyResourceResolver;

    // todo: support caching

    myLayoutLib.clearCaches(PROJECT_KEY);
    final SessionParams params =
      new SessionParams(parser, RenderingMode.NORMAL, PROJECT_KEY, dimension.width, dimension.height, density, xdpi, ydpi, resolver,
                        myProjectCallback, minSdkVersion, targetSdkVersion, log);

    params.setExtendedViewInfoMode(false);
    params.setAppLabel(appLabel);
    params.setTimeout(timeout);

    final ScreenSizeQualifier screenSizeQualifier = myConfig.getScreenSizeQualifier();
    params.setConfigScreenSize(screenSizeQualifier != null ? screenSizeQualifier.getValue() : ScreenSize.NORMAL);
    return myLayoutLib.createSession(params);
  }

  @Nullable
  private Dimension getDimension() {
    final ScreenDimensionQualifier dimensionQualifier = myConfig.getScreenDimensionQualifier();

    final int size1 = dimensionQualifier != null ? dimensionQualifier.getValue1() : 320;
    final int size2 = dimensionQualifier != null ? dimensionQualifier.getValue2() : 240;

    final ScreenOrientationQualifier orientationQualifier = myConfig.getScreenOrientationQualifier();

    final ScreenOrientation orientation = orientationQualifier != null
                                          ? orientationQualifier.getValue()
                                          : ScreenOrientation.PORTRAIT;

    switch (orientation) {
      case LANDSCAPE:
        return new Dimension(size1 < size2 ? size2 : size1, size1 < size2 ? size1 : size2);
      case PORTRAIT:
        return new Dimension(size1 < size2 ? size1 : size2, size1 < size2 ? size2 : size1);
      case SQUARE:
        return new Dimension(size1, size1);
      default:
        LOG.error("Unknown screen orientation " + orientation);
        return null;
    }
  }
}
