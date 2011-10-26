/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.compiler.tools;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.ExecutionUtil;
import org.jetbrains.annotations.NonNls;
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
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.tools.AndroidApt");

  @NonNls private static final String COMMAND_CRUNCH = "crunch";
  @NonNls private static final String COMMAND_PACKAGE = "package";

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

    //noinspection deprecation
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
    LOG.info(AndroidUtils.command2string(args));
    return ExecutionUtil.execute(ArrayUtil.toStringArray(args));
  }

  public static Map<CompilerMessageCategory, List<String>> crunch(@NotNull IAndroidTarget target,
                                                                  @NotNull List<String> resPaths,
                                                                  @NotNull String outputPath) throws IOException {
    final ArrayList<String> args = new ArrayList<String>();

    //noinspection deprecation
    args.add(target.getPath(IAndroidTarget.AAPT));

    args.add(COMMAND_CRUNCH);

    for (String path : resPaths) {
      args.add("-S");
      args.add(path);
    }

    args.add("-C");
    args.add(outputPath);

    LOG.info(AndroidUtils.command2string(args));
    return ExecutionUtil.execute(ArrayUtil.toStringArray(args));
  }

  public static Map<CompilerMessageCategory, List<String>> packageResources(@NotNull IAndroidTarget target,
                                                                            @NotNull String manifestPath,
                                                                            @NotNull String[] resPaths,
                                                                            @Nullable String osAssetsPath,
                                                                            @NotNull String outputPath,
                                                                            @Nullable String configFilter,
                                                                            boolean debugMode,
                                                                            int versionCode) throws IOException {
    final ArrayList<String> args = new ArrayList<String>();

    //noinspection deprecation
    args.add(target.getPath(IAndroidTarget.AAPT));

    args.add(COMMAND_PACKAGE);

    for (String path : resPaths) {
      args.add("-S");
      args.add(path);
    }

    args.add("-f");
    args.add("--no-crunch");

    if (resPaths.length > 1) {
      args.add("--auto-add-overlay");
    }

    if (debugMode) {
      args.add("--debug-mode");
    }

    if (versionCode > 0) {
      args.add("--version-code");
      args.add(Integer.toString(versionCode));
    }

    if (configFilter != null) {
      args.add("-c");
      args.add(configFilter);
    }

    args.add("-M");
    args.add(manifestPath);

    if (osAssetsPath != null) {
      args.add("-A");
      args.add(osAssetsPath);
    }

    args.add("-I");
    args.add(target.getPath(IAndroidTarget.ANDROID_JAR));

    args.add("-F");
    args.add(outputPath);

    LOG.info(AndroidUtils.command2string(args));
    return ExecutionUtil.execute(ArrayUtil.toStringArray(args));
  }
}
