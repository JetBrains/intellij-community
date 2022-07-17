// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.appengine.sdk.impl;

import com.intellij.appengine.JavaGoogleAppEngineBundle;
import com.intellij.facet.ui.FacetConfigurationQuickFix;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.*;
import java.util.*;

public final class AppEngineSdkUtil {
  private static final Logger LOG = Logger.getInstance(AppEngineSdkUtil.class);
  @NonNls public static final String APP_ENGINE_DOWNLOAD_URL = "https://code.google.com/appengine/downloads.html#Google_App_Engine_SDK_for_Java";
  private static final FacetConfigurationQuickFix DOWNLOAD_SDK_QUICK_FIX =
    new FacetConfigurationQuickFix(JavaGoogleAppEngineBundle.message("button.sdk.download.text")) {
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
      try (PrintWriter writer = new PrintWriter(cachedWhiteList)) {
        for (String packageName : classesWhiteList.keySet()) {
          writer.println("." + packageName);
          final Set<String> classes = classesWhiteList.get(packageName);
          for (String aClass : classes) {
            writer.println(aClass);
          }
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static Map<String, Set<String>> loadWhiteList(File input) throws IOException {
    Map<String, Set<String>> map = new HashMap<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(input))) {
      String line;
      Set<String> currentClasses = new HashSet<>();
      map.put("", currentClasses);
      while ((line = reader.readLine()) != null) {
        if (line.startsWith(".")) {
          String packageName = line.substring(1);
          currentClasses = new HashSet<>();
          map.put(packageName, currentClasses);
        }
        else {
          currentClasses.add(line);
        }
      }
    }
    return map;
  }

  public static Map<String, Set<String>> computeWhiteList(final File toolsApiJarFile) {
    try {
      Map<String, Set<String>> map = new HashMap<>();
      final ClassLoader loader = UrlClassLoader.build().files(Collections.singletonList(toolsApiJarFile.toPath())).parent(
        AppEngineSdkUtil.class.getClassLoader()).get();
      final Class<?> whiteListClass = Class.forName("com.google.apphosting.runtime.security.WhiteList", true, loader);
      final Set<String> classes = (Set<String>)whiteListClass.getMethod("getWhiteList").invoke(null);
      for (String qualifiedName : classes) {
        final String packageName = StringUtil.getPackageName(qualifiedName);
        Set<String> classNames = map.get(packageName);
        if (classNames == null) {
          classNames = new HashSet<>();
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
    return new ValidationResult(JavaGoogleAppEngineBundle.message("sdk.file.not.found.message", path, file), DOWNLOAD_SDK_QUICK_FIX);
  }
}
