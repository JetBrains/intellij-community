/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.android.util.AndroidExecutionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
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

  public static Map<AndroidCompilerMessageKind, List<String>> compile(@NotNull IAndroidTarget target,
                                                                      int platformToolsRevision,
                                                                      @NotNull String manifestFileOsPath,
                                                                      @NotNull String aPackage,
                                                                      @NotNull String outDirOsPath,
                                                                      @NotNull String[] resourceDirsOsPaths,
                                                                      @NotNull String[] libPackages,
                                                                      boolean nonConstantFields,
                                                                      @Nullable String proguardCfgOutputFileOsPath) throws IOException {
    final Map<AndroidCompilerMessageKind, List<String>> messages = new HashMap<AndroidCompilerMessageKind, List<String>>();
    messages.put(AndroidCompilerMessageKind.ERROR, new ArrayList<String>());
    messages.put(AndroidCompilerMessageKind.INFORMATION, new ArrayList<String>());

    final File outOsDir = new File(outDirOsPath);
    if (!outOsDir.exists()) {
      if (!outOsDir.mkdirs()) {
        messages.get(AndroidCompilerMessageKind.ERROR).add("Unable to create directory " + outDirOsPath);
      }
    }

    final String packageFolderOsPath = FileUtil.toSystemDependentName(outDirOsPath + '/' + aPackage.replace('.', '/'));

    /* We actually need to delete the manifest.java as it may become empty and
    in this case aapt doesn't generate an empty one, but instead doesn't
    touch it */
    final File manifestJavaFile = new File(packageFolderOsPath + File.separatorChar + AndroidCommonUtils.MANIFEST_JAVA_FILE_NAME);
    if (manifestJavaFile.exists()) {
      if (!FileUtil.delete(manifestJavaFile)) {
        messages.get(AndroidCompilerMessageKind.ERROR).add("Unable to delete " + manifestJavaFile.getPath());
      }
    }

    final File rJavaFile = new File(packageFolderOsPath + File.separatorChar + AndroidCommonUtils.R_JAVA_FILENAME);
    if (rJavaFile.exists()) {
      if (!FileUtil.delete(rJavaFile)) {
        messages.get(AndroidCompilerMessageKind.ERROR).add("Unable to delete " + rJavaFile.getPath());
      }
    }

    final File[] libRJavaFiles = new File[libPackages.length];

    for (int i = 0; i < libPackages.length; i++) {
      final String libPackageFolderOsPath = FileUtil.toSystemDependentName(outDirOsPath + '/' + libPackages[i].replace('.', '/'));
      libRJavaFiles[i] = new File(libPackageFolderOsPath + File.separatorChar + AndroidCommonUtils.R_JAVA_FILENAME);
    }

    for (File libRJavaFile : libRJavaFiles) {
      if (libRJavaFile.exists()) {
        if (!FileUtil.delete(libRJavaFile)) {
          messages.get(AndroidCompilerMessageKind.ERROR).add("Unable to delete " + libRJavaFile.getPath());
        }
      }
    }

    if (platformToolsRevision < 0 || platformToolsRevision > 7) {
      Map<AndroidCompilerMessageKind, List<String>> map =
        doCompile(target, manifestFileOsPath, outDirOsPath, resourceDirsOsPaths, libPackages, null, nonConstantFields,
                  proguardCfgOutputFileOsPath);

      if (map.get(AndroidCompilerMessageKind.ERROR).isEmpty()) {
        makeFieldsNotFinal(libRJavaFiles);
      }

      AndroidExecutionUtil.addMessages(messages, map);
      return messages;
    }
    else {
      Map<AndroidCompilerMessageKind, List<String>> map;

      map = doCompile(target, manifestFileOsPath, outDirOsPath, resourceDirsOsPaths, ArrayUtil.EMPTY_STRING_ARRAY, null, false,
                      proguardCfgOutputFileOsPath);
      AndroidExecutionUtil.addMessages(messages, map);

      for (String libPackage : libPackages) {
        map = doCompile(target, manifestFileOsPath, outDirOsPath, resourceDirsOsPaths, ArrayUtil.EMPTY_STRING_ARRAY, libPackage, false,
                        proguardCfgOutputFileOsPath);
        AndroidExecutionUtil.addMessages(messages, map);
      }
      return messages;
    }
  }

  private static void makeFieldsNotFinal(@NotNull File[] libRJavaFiles) throws IOException {
    for (File file : libRJavaFiles) {
      if (file.isFile()) {
        final String fileContent = AndroidCommonUtils.readFile(file);
        FileUtil.writeToFile(file, fileContent.replace("public static final int ", "public static int "));
      }
    }
  }

  private static Map<AndroidCompilerMessageKind, List<String>> doCompile(@NotNull IAndroidTarget target,
                                                                         @NotNull String manifestFileOsPath,
                                                                         @NotNull String outDirOsPath,
                                                                         @NotNull String[] resourceDirsOsPaths,
                                                                         @NotNull String[] extraPackages,
                                                                         @Nullable String customPackage,
                                                                         boolean nonConstantIds,
                                                                         @Nullable String proguardCfgOutputFileOsPath)
    throws IOException {
    final List<String> args = new ArrayList<String>();

    args.add(target.getPath(IAndroidTarget.AAPT));
    args.add("package");
    args.add("-m");

    if (nonConstantIds) {
      args.add("--non-constant-id");
    }

    if (resourceDirsOsPaths.length > 1) {
      args.add("--auto-add-overlay");
    }

    if (extraPackages.length > 0) {
      args.add("--extra-packages");
      args.add(toPackagesString(extraPackages));
    }

    if (customPackage != null) {
      args.add("--custom-package");
      args.add(customPackage);
    }

    args.add("-J");
    args.add(outDirOsPath);
    args.add("-M");
    args.add(manifestFileOsPath);

    for (String libResFolderOsPath : resourceDirsOsPaths) {
      args.add("-S");
      args.add(libResFolderOsPath);
    }

    args.add("-I");
    args.add(target.getPath(IAndroidTarget.ANDROID_JAR));

    if (proguardCfgOutputFileOsPath != null) {
      args.add("-G");
      args.add(proguardCfgOutputFileOsPath);
    }
    LOG.info(AndroidCommonUtils.command2string(args));
    return AndroidExecutionUtil.doExecute(ArrayUtil.toStringArray(args));
  }

  @NotNull
  private static String toPackagesString(@NotNull String[] packages) {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0, n = packages.length; i < n; i++) {
      if (i > 0) {
        builder.append(':');
      }
      builder.append(packages[i]);
    }
    return builder.toString();
  }

  public static Map<AndroidCompilerMessageKind, List<String>> crunch(@NotNull IAndroidTarget target,
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

    LOG.info(AndroidCommonUtils.command2string(args));
    return AndroidExecutionUtil.doExecute(ArrayUtil.toStringArray(args));
  }

  public static Map<AndroidCompilerMessageKind, List<String>> packageResources(@NotNull IAndroidTarget target,
                                                                               int platformToolsRevision,
                                                                               @NotNull String manifestPath,
                                                                               @NotNull String[] resPaths,
                                                                               @NotNull String[] osAssetDirPaths,
                                                                               @NotNull String outputPath,
                                                                               @Nullable String configFilter,
                                                                               boolean debugMode,
                                                                               int versionCode,
                                                                               FileFilter assetsFilter) throws IOException {
    for (String resDirPath : resPaths) {
      if (FileUtil.isAncestor(resDirPath, outputPath, false)) {
        throw new IOException("Resource directory " +
                              FileUtil.toSystemDependentName(resDirPath) +
                              " contains output " +
                              FileUtil.toSystemDependentName(outputPath));
      }
    }

    for (String assetsDirPath : osAssetDirPaths) {
      if (FileUtil.isAncestor(assetsDirPath, outputPath, false)) {
        throw new IOException("Assets directory " +
                              FileUtil.toSystemDependentName(assetsDirPath) +
                              " contains output " +
                              FileUtil.toSystemDependentName(outputPath));
      }
    }

    final ArrayList<String> args = new ArrayList<String>();

    //noinspection deprecation
    args.add(target.getPath(IAndroidTarget.AAPT));

    args.add(COMMAND_PACKAGE);

    for (String path : resPaths) {
      args.add("-S");
      args.add(path);
    }

    args.add("-f");

    if (platformToolsRevision < 0 || platformToolsRevision > 7) {
      args.add("--no-crunch");
    }

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

    File tempDir = null;
    try {
      if (osAssetDirPaths.length > 0) {
        final String[] nonEmptyAssetDirs = getNonEmptyExistingDirs(osAssetDirPaths);

        if (nonEmptyAssetDirs.length > 0) {
          if (nonEmptyAssetDirs.length == 1) {
            args.add("-A");
            args.add(nonEmptyAssetDirs[0]);
          }
          else {
            tempDir = FileUtil.createTempDirectory("android_combined_assets", "tmp");
            for (int i = nonEmptyAssetDirs.length - 1; i >= 0; i--) {
              final String assetDir = nonEmptyAssetDirs[i];
              FileUtil.copyDir(new File(assetDir), tempDir, assetsFilter);
            }
            args.add("-A");
            args.add(tempDir.getPath());
          }
        }
      }

      args.add("-I");
      args.add(target.getPath(IAndroidTarget.ANDROID_JAR));

      args.add("-F");
      args.add(outputPath);

      LOG.info(AndroidCommonUtils.command2string(args));
      return AndroidExecutionUtil.doExecute(ArrayUtil.toStringArray(args));
    }
    finally {
      if (tempDir != null) {
        FileUtil.delete(tempDir);
      }
    }
  }

  @NotNull
  private static String[] getNonEmptyExistingDirs(@NotNull String[] dirs) {
    final List<String> result = new ArrayList<String>();
    for (String dirPath : dirs) {
      final File dir = new File(dirPath);

      if (dir.isDirectory()) {
        final File[] children = dir.listFiles();

        if (children != null && children.length > 0) {
          result.add(dirPath);
        }
      }
    }
    return ArrayUtil.toStringArray(result);
  }
}
