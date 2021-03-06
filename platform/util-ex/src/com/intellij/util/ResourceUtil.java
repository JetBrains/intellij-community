// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ResourceUtil {
  private ResourceUtil() {
  }

  /**
   * @deprecated Use {@link #getResourceAsStream(ClassLoader, String, String)}
   */
  @Deprecated
  public static URL getResource(@NotNull Class<?> loaderClass, @NonNls @NotNull String basePath, @NonNls @NotNull String fileName) {
    return getResource(loaderClass.getClassLoader(), basePath, fileName);
  }

  /**
   * @deprecated Use {@link #getResourceAsStream(ClassLoader, String, String)}
   */
  @Deprecated
  public static InputStream getResourceAsStream(@NotNull Class<?> loaderClass, @NonNls @NotNull String basePath, @NonNls @NotNull String fileName) {
    return getResourceAsStream(loaderClass.getClassLoader(), basePath, fileName);
  }

  public static InputStream getResourceAsStream(@NotNull ClassLoader loader, @NonNls @NotNull String basePath, @NonNls @NotNull String fileName) {
    String fixedPath = StringUtil.trimStart(Strings.trimEnd(basePath, "/"), "/");
    if (fixedPath.isEmpty()) {
      return loader.getResourceAsStream(fileName);
    }

    List<String> bundles = calculateBundleNames(fixedPath, Locale.getDefault());
    for (String bundle : bundles) {
      InputStream stream = loader.getResourceAsStream(bundle + "/" + fileName);
      if (stream == null) {
        continue;
      }
      return stream;
    }

    return loader.getResourceAsStream(fixedPath + "/" + fileName);
  }

  public static URL getResource(@NotNull ClassLoader loader, @NonNls @NotNull String basePath, @NonNls @NotNull String fileName) {
    String fixedPath = StringUtil.trimStart(Strings.trimEnd(basePath, "/"), "/");

    List<String> bundles = calculateBundleNames(fixedPath, Locale.getDefault());
    for (String bundle : bundles) {
      URL url = loader.getResource(bundle + "/" + fileName);
      if (url == null) {
        continue;
      }

      try {
        url.openConnection();
      }
      catch (IOException e) {
        continue;
      }

      return url;
    }

    return loader.getResource(fixedPath + "/" + fileName);
  }

  /**
   * Copied from java.util.ResourceBundle implementation
   */
  private static @NotNull List<String> calculateBundleNames(@NotNull String baseName, @NotNull Locale locale) {
    final List<String> result = new ArrayList<>(3);

    result.add(0, baseName);

    final String language = locale.getLanguage();
    final int languageLength = language.length();
    final String country = locale.getCountry();
    final int countryLength = country.length();
    final String variant = locale.getVariant();
    final int variantLength = variant.length();
    if (languageLength + countryLength + variantLength == 0) {
      //The locale is "", "", "".
      return result;
    }

    final StringBuilder temp = new StringBuilder(baseName);
    temp.append('_');
    temp.append(language);
    if (languageLength > 0) {
      result.add(0, temp.toString());
    }

    if (countryLength + variantLength == 0) {
      return result;
    }

    temp.append('_');
    temp.append(country);
    if (countryLength > 0) {
      result.add(0, temp.toString());
    }

    if (variantLength == 0) {
      return result;
    }
    temp.append('_');
    temp.append(variant);
    result.add(0, temp.toString());

    return result;
  }

  /**
   * @deprecated Use {@link #loadText(InputStream)}
   */
  @Deprecated
  public static @NotNull String loadText(@NotNull URL url) throws IOException {
    return loadText(URLUtil.openStream(url));
  }

  public static @NotNull String loadText(@NotNull InputStream in) throws IOException {
    try {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    finally {
      in.close();
    }
  }
}
