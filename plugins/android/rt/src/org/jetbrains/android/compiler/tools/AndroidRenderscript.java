package org.jetbrains.android.compiler.tools;

import com.android.SdkConstants;
import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.android.util.AndroidExecutionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidRenderscript {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidRenderscriptCompiler");

  public static Map<AndroidCompilerMessageKind, List<String>> execute(@NotNull final String sdkLocation,
                                                                      @NotNull IAndroidTarget target,
                                                                      @NotNull String sourceFilePath,
                                                                      @NotNull final String genFolderPath,
                                                                      @Nullable String depFolderPath,
                                                                      @NotNull final String rawDirPath)
    throws IOException {
    final List<String> command = new ArrayList<String>();
    command.add(
      FileUtil.toSystemDependentName(sdkLocation + '/' + SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER + SdkConstants.FN_RENDERSCRIPT));
    command.add("-I");
    command.add(target.getPath(IAndroidTarget.ANDROID_RS_CLANG));
    command.add("-I");
    command.add(target.getPath(IAndroidTarget.ANDROID_RS));
    command.add("-p");
    command.add(FileUtil.toSystemDependentName(genFolderPath));
    command.add("-o");
    command.add(FileUtil.toSystemDependentName(rawDirPath));

    command.add("-target-api");
    int targetApi = target.getVersion().getApiLevel();
    if (targetApi < 11) {
      targetApi = 11;
    }
    command.add(Integer.toString(targetApi));

    if (depFolderPath != null) {
      command.add("-d");
      command.add(FileUtil.toSystemDependentName(depFolderPath));
    }

    command.add("-MD");
    command.add(FileUtil.toSystemDependentName(sourceFilePath));

    LOG.info(AndroidCommonUtils.command2string(command));
    return AndroidExecutionUtil.doExecute(ArrayUtil.toStringArray(command));
  }
}
