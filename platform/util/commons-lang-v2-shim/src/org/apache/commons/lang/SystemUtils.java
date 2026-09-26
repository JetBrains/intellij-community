// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.apache.commons.lang;

/**
 * @deprecated To continue using the functionality offered by commons-lang2,
 * please consider migrating to either the commons-lang3 or commons-text libraries and bundling them with your plugin.
 * Or consider using the corresponding API from IJ Platform.
 */
@SuppressWarnings({"unused", "SameParameterValue"})
@Deprecated(forRemoval = true)
public final class SystemUtils extends org.apache.commons.lang3.SystemUtils {
  public static final float JAVA_VERSION_FLOAT = getJavaVersionAsFloat();

  private static float getJavaVersionAsFloat() {
    return toVersionFloat(toJavaVersionIntArray(JAVA_VERSION, 3));
  }

  private static int[] toJavaVersionIntArray(String version, int limit) {
    if (version == null) {
      return new int[0];
    }
    else {
      String[] strings = org.apache.commons.lang3.StringUtils.split(version, "._- ");
      int[] ints = new int[Math.min(limit, strings.length)];
      int j = 0;

      for (int i = 0; i < strings.length && j < limit; ++i) {
        String s = strings[i];
        if (!s.isEmpty()) {
          try {
            ints[j] = Integer.parseInt(s);
            ++j;
          }
          catch (Exception ignored) {
          }
        }
      }

      if (ints.length > j) {
        int[] newInts = new int[j];
        System.arraycopy(ints, 0, newInts, 0, j);
        ints = newInts;
      }

      return ints;
    }
  }

  private static float toVersionFloat(int[] javaVersions) {
    if (javaVersions != null && javaVersions.length != 0) {
      if (javaVersions.length == 1) {
        return (float)javaVersions[0];
      }
      else {
        StringBuilder builder = new StringBuilder();
        builder.append(javaVersions[0]);
        builder.append('.');

        for (int i = 1; i < javaVersions.length; ++i) {
          builder.append(javaVersions[i]);
        }

        try {
          return Float.parseFloat(builder.toString());
        }
        catch (Exception var3) {
          return 0.0F;
        }
      }
    }
    else {
      return 0.0F;
    }
  }
}
