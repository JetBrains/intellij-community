// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.ex;

import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.URLUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public final class DecodeDefaultsUtil {
  private static final Logger LOG = Logger.getInstance(DecodeDefaultsUtil.class);

  public static URL getDefaults(Object requestor, @NotNull String componentResourcePath) {
    URL url;
    if (componentResourcePath.charAt(0) == '/') {
      url = getResource(appendExt(componentResourcePath), requestor);
      if (url == null && !(requestor instanceof UrlClassLoader)) {
        url = getResource(appendExt(componentResourcePath.substring(1)), requestor);
      }
    }
    else {
      url = getResource(appendExt("/idea/" + componentResourcePath), requestor);
      if (url == null) {
        String path = requestor instanceof ClassLoader ? appendExt(componentResourcePath) : appendExt('/' + componentResourcePath);
        url = getResource(path, requestor);
      }
    }
    return url;
  }

  private static URL getResource(String path, Object requestor) {
    if (requestor instanceof ClassLoader) {
      return ((ClassLoader)requestor).getResource(path);
    }
    return requestor.getClass().getResource(path);
  }

  private static String appendExt(@NotNull String s) {
    return s.endsWith(FileStorageCoreUtil.DEFAULT_EXT) ? s : s + FileStorageCoreUtil.DEFAULT_EXT;
  }

  public static @Nullable InputStream getDefaultsInputStream(Object requestor, @NotNull String componentResourcePath) {
    try {
      URL defaults = getDefaults(requestor, componentResourcePath);
      return defaults == null ? null : URLUtil.openStream(defaults);
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }
}
