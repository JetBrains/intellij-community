package org.jetbrains.android.util;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidCommonUtils {
  @NonNls public static final Object MANIFEST_JAVA_FILE_NAME = "Manifest.java";
  public static final String R_JAVA_FILENAME = "R.java";

  private AndroidCommonUtils() {
  }

  public static String command2string(@NotNull Collection<String> command) {
    final StringBuilder builder = new StringBuilder();
    for (Iterator<String> it = command.iterator(); it.hasNext(); ) {
      String s = it.next();
      builder.append('[');
      builder.append(s);
      builder.append(']');
      if (it.hasNext()) {
        builder.append(' ');
      }
    }
    return builder.toString();
  }
}
