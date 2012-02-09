package org.jetbrains.android.util;

import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidCommonUtils {
  @NonNls public static final Object MANIFEST_JAVA_FILE_NAME = "Manifest.java";
  @NonNls public static final String R_JAVA_FILENAME = "R.java";

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

  @Nullable
  public static SdkManager createSdkManager(@NotNull String path, @NotNull ISdkLog log) {
    path = FileUtil.toSystemDependentName(path);

    final File f = new File(path);
    if (!f.exists() || !f.isDirectory()) {
      return null;
    }

    final File platformsDir = new File(f, SdkConstants.FD_PLATFORMS);
    if (!platformsDir.exists() || !platformsDir.isDirectory()) {
      return null;
    }

    return SdkManager.createManager(path + File.separatorChar, log);
  }

  public static void moveAllFiles(@NotNull File from, @NotNull File to, @NotNull Collection<File> newFiles) throws IOException {
    if (from.isFile()) {
      FileUtil.rename(from, to);
      newFiles.add(to);
    }
    else {
      final File[] children = from.listFiles();

      if (children != null) {
        for (File child : children) {
          moveAllFiles(child, new File(to, child.getName()), newFiles);
        }
      }
    }
  }
}
