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
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineBuilder;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDxWrapper {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.tools.AndroidDx");

  private AndroidDxWrapper() {
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  public static Map<AndroidCompilerMessageKind, List<String>> execute(@NotNull Module module,
                                                                      @NotNull IAndroidTarget target,
                                                                      @NotNull String outputDir,
                                                                      @NotNull String[] compileTargets,
                                                                      @NotNull String additionalVmParams,
                                                                      int maxHeapSize,
                                                                      boolean optimize) {
    String outFile = outputDir + File.separatorChar + AndroidCommonUtils.CLASSES_FILE_NAME;

    final Map<AndroidCompilerMessageKind, List<String>> messages = new HashMap<AndroidCompilerMessageKind, List<String>>(2);
    messages.put(AndroidCompilerMessageKind.ERROR, new ArrayList<String>());
    messages.put(AndroidCompilerMessageKind.INFORMATION, new ArrayList<String>());
    messages.put(AndroidCompilerMessageKind.WARNING, new ArrayList<String>());

    @SuppressWarnings("deprecation")
    String dxJarPath = target.getPath(IAndroidTarget.DX_JAR);

    File dxJar = new File(dxJarPath);
    if (!dxJar.isFile()) {
      messages.get(AndroidCompilerMessageKind.ERROR).add(AndroidBundle.message("android.file.not.exist.error", dxJarPath));
      return messages;
    }

    JavaParameters parameters = new JavaParameters();
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();

    // dex runs after simple java compilation, so JDK must be specified
    assert sdk != null;

    parameters.setJdk(sdk);
    parameters.setMainClass(AndroidDxRunner.class.getName());

    ParametersList programParamList = parameters.getProgramParametersList();
    programParamList.add(dxJarPath);
    programParamList.add(outFile);
    programParamList.add("--optimize", Boolean.toString(optimize));
    programParamList.addAll(compileTargets);
    programParamList.add("--exclude");

    ParametersList vmParamList = parameters.getVMParametersList();

    if (additionalVmParams.length() > 0) {
      vmParamList.addParametersString(additionalVmParams);
    }
    if (!AndroidCommonUtils.hasXmxParam(vmParamList.getParameters())) {
      vmParamList.add("-Xmx" + maxHeapSize + "M");
    }
    final PathsList classPath = parameters.getClassPath();
    classPath.add(PathUtil.getJarPathForClass(AndroidDxRunner.class));
    classPath.add(PathUtil.getJarPathForClass(FileUtilRt.class));

    // delete file to check if it will exist after dex compilation
    if (!new File(outFile).delete()) {
      LOG.info("Cannot delete file " + outFile);
    }

    Process process;
    try {
      GeneralCommandLine commandLine = CommandLineBuilder.createFromJavaParameters(parameters, true);
      LOG.info(commandLine.getCommandLineString());
      process = commandLine.createProcess();
    }
    catch (ExecutionException e) {
      messages.get(AndroidCompilerMessageKind.ERROR).add("ExecutionException: " + e.getMessage());
      LOG.info(e);
      return messages;
    }

    AndroidCommonUtils.handleDexCompilationResult(process, outFile, messages);

    return messages;
  }
}
