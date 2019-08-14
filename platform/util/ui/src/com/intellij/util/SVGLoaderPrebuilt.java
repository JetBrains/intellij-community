// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.icons.IconLoadMeasurer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

@ApiStatus.Internal
public class SVGLoaderPrebuilt {

  @NotNull
  @ApiStatus.Internal
  public static String getPreBuiltImageURLSuffix(double scale) {
    //noinspection SpellCheckingInspection
    return "-" + scale + ".jpix";
  }

  @Nullable
  private static URL preBuiltImageURL(@Nullable URL url, double scale) {
    if (url == null) return null;
    try {
      return new URL(url, url.toString() + getPreBuiltImageURLSuffix(scale));
    }
    catch (MalformedURLException e) {
      return null;
    }
  }

  @Nullable
  @ApiStatus.Internal
  public static BufferedImage loadUrlFromPreBuiltCache(@Nullable URL url,
                                                       double scale,
                                                       @NotNull ImageLoader.Dimension2DDouble docSize) {
    long start = StartUpMeasurer.isEnabled() ? StartUpMeasurer.getCurrentTime() : -1;

    URL lookupUrl = preBuiltImageURL(url, scale);
    if (lookupUrl == null) return null;

    try (InputStream is = lookupUrl.openStream()) {
      BufferedImage result = SVGLoaderCacheIO.readImageFile(FileUtil.loadBytes(is), docSize);
      IconLoadMeasurer.svgPreBuiltLoad.addDurationStartedAt(start);
      return result;
    }
    catch (IOException e) {
      return null;
    }
  }
}
