/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.appengine.sdk.impl;

import com.intellij.facet.ui.FacetConfigurationQuickFix;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.lang.UrlClassLoader;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.*;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author nik
 */
public class AppEngineSdkUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.appengine.sdk.impl.AppEngineSdkUtil");
  @NonNls public static final String APP_ENGINE_DOWNLOAD_URL = "http://code.google.com/appengine/downloads.html#Google_App_Engine_SDK_for_Java";
  private static final FacetConfigurationQuickFix DOWNLOAD_SDK_QUICK_FIX = new FacetConfigurationQuickFix("Download...") {
    @Override
    public void run(JComponent place) {
      BrowserUtil.browse(APP_ENGINE_DOWNLOAD_URL);
    }
  };

  private AppEngineSdkUtil() {
  }

  public static void saveWhiteList(File cachedWhiteList, Map<String, Set<String>> classesWhiteList) {
    try {
      FileUtil.createParentDirs(cachedWhiteList);
      PrintWriter writer = new PrintWriter(cachedWhiteList);
      try {
        for (String packageName : classesWhiteList.keySet()) {
          writer.println("." + packageName);
          final Set<String> classes = classesWhiteList.get(packageName);
          for (String aClass : classes) {
            writer.println(aClass);
          }
        }
      }
      finally {
        writer.close();
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static Map<String, Set<String>> loadWhiteList(File input) throws IOException {
    final THashMap<String, Set<String>> map = new THashMap<>();
    BufferedReader reader = new BufferedReader(new FileReader(input));
    try {
      String line;
      Set<String> currentClasses = new THashSet<>();
      map.put("", currentClasses);
      while ((line = reader.readLine()) != null) {
        if (line.startsWith(".")) {
          String packageName = line.substring(1);
          currentClasses = new THashSet<>();
          map.put(packageName, currentClasses);
        }
        else {
          currentClasses.add(line);
        }
      }
    }
    finally {
      reader.close();
    }
    return map;
  }

  public static Map<String, Set<String>> computeWhiteList(final File toolsApiJarFile) {
    try {
      final THashMap<String, Set<String>> map = new THashMap<>();
      final ClassLoader loader = UrlClassLoader.build().urls(toolsApiJarFile.toURI().toURL()).parent(
        AppEngineSdkUtil.class.getClassLoader()).get();
      final Class<?> whiteListClass = Class.forName("com.google.apphosting.runtime.security.WhiteList", true, loader);
      final Set<String> classes = (Set<String>)whiteListClass.getMethod("getWhiteList").invoke(null);
      for (String qualifiedName : classes) {
        final String packageName = StringUtil.getPackageName(qualifiedName);
        Set<String> classNames = map.get(packageName);
        if (classNames == null) {
          classNames = new THashSet<>();
          map.put(packageName, classNames);
        }
        classNames.add(StringUtil.getShortName(qualifiedName));
      }
      return map;
    }
    catch (UnsupportedClassVersionError e) {
      LOG.warn(e);
      return Collections.emptyMap();
    }
    catch (Exception e) {
      LOG.error(e);
      return Collections.emptyMap();
    }
  }

  @NotNull
  public static ValidationResult checkPath(String path) {
    final AppEngineSdkImpl sdk = new AppEngineSdkImpl(path);

    final File appCfgFile = sdk.getAppCfgFile();
    if (!appCfgFile.exists()) {
      return createNotFoundMessage(path, appCfgFile);
    }

    final File toolsApiJarFile = sdk.getToolsApiJarFile();
    if (!toolsApiJarFile.exists()) {
      return createNotFoundMessage(path, toolsApiJarFile);
    }

    return ValidationResult.OK;
  }

  private static ValidationResult createNotFoundMessage(@NotNull String path, @NotNull File file) {
    return new ValidationResult("'" + path + "' is not valid App Engine SDK installation: " + "'" + file + "' file not found",
                                DOWNLOAD_SDK_QUICK_FIX);
  }
}
