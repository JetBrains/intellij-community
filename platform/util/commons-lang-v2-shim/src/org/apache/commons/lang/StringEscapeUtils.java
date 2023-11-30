// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.apache.commons.lang;

/**
 * @deprecated To continue using the functionality offered by commons-lang2,
 * please consider migrating to either the commons-lang3 or commons-text libraries and bundling them with your plugin.
 * Or consider using the corresponding API from IJ Platform.
 */
@SuppressWarnings("unused")
@Deprecated(forRemoval = true)
public final class StringEscapeUtils extends org.apache.commons.lang3.StringEscapeUtils {
  public static String escapeHtml(String input) {
    return escapeHtml4(input);
  }

  public static String unescapeHtml(String input) {
    return unescapeHtml4(input);
  }

  public static String escapeJavaScript(String input) {
    return escapeEcmaScript(input);
  }

  public static String unescapeJavaScript(String input) {
    return unescapeEcmaScript(input);
  }
}
