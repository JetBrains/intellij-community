package org.jetbrains.android.util;

import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidCommonUtils {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.util.AndroidCommonUtils");

  @NonNls public static final Object MANIFEST_JAVA_FILE_NAME = "Manifest.java";
  @NonNls public static final String R_JAVA_FILENAME = "R.java";
  @NonNls public static final String CLASSES_JAR_FILE_NAME = "classes.jar";
  @NonNls public static final String CLASSES_FILE_NAME = "classes.dex";
  private static final Pattern WARNING_PATTERN = Pattern.compile(".*warning.*");
  private static final Pattern ERROR_PATTERN = Pattern.compile(".*error.*");
  private static final Pattern EXCEPTION_PATTERN = Pattern.compile(".*exception.*");

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

  public static void handleDexCompilationResult(@NotNull Process process,
                                                @NotNull String outputFilePath,
                                                @NotNull final Map<AndroidCompilerMessageKind, List<String>> messages) {
    final BaseOSProcessHandler handler = new BaseOSProcessHandler(process, null, null);
    handler.addProcessListener(new ProcessAdapter() {
      private AndroidCompilerMessageKind myCategory = null;

      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        String[] msgs = event.getText().split("\\n");
        for (String msg : msgs) {
          msg = msg.trim();
          String msglc = msg.toLowerCase();
          if (outputType == ProcessOutputTypes.STDERR) {
            if (WARNING_PATTERN.matcher(msglc).matches()) {
              myCategory = AndroidCompilerMessageKind.WARNING;
            }
            if (ERROR_PATTERN.matcher(msglc).matches() || EXCEPTION_PATTERN.matcher(msglc).matches() || myCategory == null) {
              myCategory = AndroidCompilerMessageKind.ERROR;
            }
            messages.get(myCategory).add(msg);
          }
          else if (outputType == ProcessOutputTypes.STDOUT) {
            if (!msglc.startsWith("processing")) {
              messages.get(AndroidCompilerMessageKind.INFORMATION).add(msg);
            }
          }

          LOG.info(msg);
        }
      }
    });

    handler.startNotify();
    handler.waitFor();

    final List<String> errors = messages.get(AndroidCompilerMessageKind.ERROR);

    if (new File(outputFilePath).isFile()) {
      // if compilation finished correctly, show all errors as warnings
      messages.get(AndroidCompilerMessageKind.WARNING).addAll(errors);
      errors.clear();
    }
    else if (errors.size() == 0) {
      errors.add("Cannot create classes.dex file");
    }
  }
}
