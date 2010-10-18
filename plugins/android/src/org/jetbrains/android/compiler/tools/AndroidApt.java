package org.jetbrains.android.compiler.tools;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.util.ExecutionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AndroidApt decorator.
 *
 * @author Alexey Efimov
 */
public final class AndroidApt {
  private AndroidApt() {
  }

  @NotNull
  public static Map<CompilerMessageCategory, List<String>> compile(@NotNull IAndroidTarget target,
                                                                   @NotNull String manifestPath,
                                                                   @NotNull String outDir,
                                                                   @NotNull String[] resourceDirs,
                                                                   @Nullable String assertsDir,
                                                                   @Nullable String customPackage) throws IOException {
    List<String> args = new ArrayList<String>();
    Collections.addAll(args, target.getPath(IAndroidTarget.AAPT), "package", "-m", "-J", outDir, "-M", manifestPath);
    if (resourceDirs.length > 1) {
      args.add("--auto-add-overlay");
    }
    for (String resourceDir : resourceDirs) {
      args.add("-S");
      args.add(resourceDir);
    }
    if (customPackage != null) {
      args.add("--custom-package");
      args.add(customPackage);
    }
    if (assertsDir != null) {
      Collections.addAll(args, "-A", assertsDir);
    }
    Collections.addAll(args, "-I", target.getPath(IAndroidTarget.ANDROID_JAR));
    return ExecutionUtil.execute(ArrayUtil.toStringArray(args));
  }

  @NotNull
  public static Map<CompilerMessageCategory, List<String>> packageResources(@NotNull IAndroidTarget target,
                                                                            @NotNull String manifestPath,
                                                                            @NotNull String[] resourceDirs,
                                                                            @Nullable String assetsDir,
                                                                            @NotNull String outputPath) throws IOException {
    List<String> args = new ArrayList<String>();
    Collections.addAll(args, target.getPath(IAndroidTarget.AAPT), "package", "-f",     // force overwrite of existing files
                       "-M", manifestPath);
    if (resourceDirs.length > 1) {
      args.add("--auto-add-overlay");
    }
    for (String resourceDir : resourceDirs) {
      args.add("-S");
      args.add(resourceDir);
    }
    if (assetsDir != null) {
      Collections.addAll(args, "-A", assetsDir);
    }
    Collections.addAll(args, "-I", target.getPath(IAndroidTarget.ANDROID_JAR), "-F", outputPath);
    return ExecutionUtil.execute(ArrayUtil.toStringArray(args));
  }
}
