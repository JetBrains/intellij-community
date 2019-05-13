/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.application.ex;

import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

public class DecodeDefaultsUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.ex.DecodeDefaultsUtil");
  private static final Map<String, URL> RESOURCE_CACHE = Collections.synchronizedMap(new THashMap<String, URL>());

  public static URL getDefaults(Object requestor, @NotNull String componentResourcePath) {
    URL url = RESOURCE_CACHE.get(componentResourcePath);
    if (url == null) {
      if (StringUtil.startsWithChar(componentResourcePath, '/')) {
        url = getResource(appendExt(componentResourcePath), requestor);
      }
      else {
        url = getResource(appendExt('/' + ApplicationManagerEx.getApplicationEx().getName() + '/' + componentResourcePath), requestor);
        if (url == null) {
          url = getResource(appendExt('/' + componentResourcePath), requestor);
        }
      }
      RESOURCE_CACHE.put(componentResourcePath, url);
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
    return appendIfNeeded(s, FileStorageCoreUtil.DEFAULT_EXT);
  }

  private static String appendIfNeeded(@NotNull String head, @NotNull String tail) {
    return head.endsWith(tail) ? head : head + tail;
  }

  @Nullable
  public static InputStream getDefaultsInputStream(Object requestor, @NotNull String componentResourcePath) {
    try {
      final URL defaults = getDefaults(requestor, componentResourcePath);
      return defaults == null ? null : URLUtil.openStream(defaults);
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }
}
