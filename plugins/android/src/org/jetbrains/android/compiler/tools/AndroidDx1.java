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
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineBuilder;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.compiler.AndroidDexCompilerConfiguration;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDx1 {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.tools.AndroidDx");

  @NonNls private static final String DEX_MAIN = "com.android.dx.command.dexer.Main";

  private static final Pattern WARNING_PATTERN = Pattern.compile(".*warning.*");
  private static final Pattern ERROR_PATTERN = Pattern.compile(".*error.*");
  private static final Pattern EXCEPTION_PATTERN = Pattern.compile(".*exception.*");

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  public Map<CompilerMessageCategory, List<String>> execute(@NotNull Module module,
                                                            @NotNull IAndroidTarget target,
                                                            @NotNull String outputDir,
                                                            @NotNull String[] compileTargets,
                                                            @NotNull String[] excluded) {
    String outFile = outputDir + File.separatorChar + "classes.dex";

    final Map<CompilerMessageCategory, List<String>> messages = new HashMap<CompilerMessageCategory, List<String>>(2);
    messages.put(CompilerMessageCategory.ERROR, new ArrayList<String>());
    messages.put(CompilerMessageCategory.INFORMATION, new ArrayList<String>());
    messages.put(CompilerMessageCategory.WARNING, new ArrayList<String>());

    String dxJarPath = target.getPath(IAndroidTarget.DX_JAR);
    File dxJar = new File(dxJarPath);
    if (!dxJar.isFile()) {
      messages.get(CompilerMessageCategory.ERROR).add(AndroidBundle.message("android.file.not.exist.error", dxJarPath));
      return messages;
    }

    JavaParameters parameters = new JavaParameters();
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();

    // dex runs after simple java compilation, so JDK must be specified
    assert sdk != null;

    parameters.setJdk(sdk);
    parameters.setMainClass(AndroidDxRunner.class.getName());
    //ParametersList programParamList = parameters.getProgramParametersList();
    ////params.add("--verbose");
    //programParamList.add("--no-strict");
    //programParamList.add("--output=" + outFile);
    //programParamList.addAll(compileTargets);

    ParametersList programParamList = parameters.getProgramParametersList();
    programParamList.add(dxJarPath);
    programParamList.add(outFile);
    programParamList.addAll(compileTargets);
    programParamList.add("--exclude");
    programParamList.addAll(excluded);

    ParametersList vmParamList = parameters.getVMParametersList();

    AndroidDexCompilerConfiguration configuration = AndroidDexCompilerConfiguration.getInstance(module.getProject());
    String additionalVmParams = configuration.VM_OPTIONS;
    if (additionalVmParams.length() > 0) {
      vmParamList.addParametersString(additionalVmParams);
    }
    if (!hasXmxParam(vmParamList)) {
      vmParamList.add("-Xmx" + configuration.MAX_HEAP_SIZE + "M");
    }
    parameters.getClassPath().add(PathUtil.getJarPathForClass(AndroidDxRunner.class));

    // delete file to check if it will exist after dex compilation
    new File(outFile).delete();

    Process process = null;
    try {
      GeneralCommandLine commandLine = CommandLineBuilder.createFromJavaParameters(parameters, true);
      LOG.info(commandLine.getCommandLineString());
      process = commandLine.createProcess();
    }
    catch (ExecutionException e) {
      messages.get(CompilerMessageCategory.ERROR).add("ExecutionException: " + e.getMessage());
      LOG.info(e);
    }

    final OSProcessHandler handler = new OSProcessHandler(process, "");
    handler.addProcessListener(new ProcessAdapter() {
      private CompilerMessageCategory myCategory = null;

      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        String[] msgs = event.getText().split("\\n");
        for (String msg : msgs) {
          msg = msg.trim();
          String msglc = msg.toLowerCase();
          if (outputType == ProcessOutputTypes.STDERR) {
            if (WARNING_PATTERN.matcher(msglc).matches()) {
              myCategory = CompilerMessageCategory.WARNING;
            }
            if (ERROR_PATTERN.matcher(msglc).matches() || EXCEPTION_PATTERN.matcher(msglc).matches() || myCategory == null) {
              myCategory = CompilerMessageCategory.ERROR;
            }
            messages.get(myCategory).add(msg);
          }
          else if (outputType == ProcessOutputTypes.STDOUT) {
            if (!msglc.startsWith("processing")) {
              messages.get(CompilerMessageCategory.INFORMATION).add(msg);
            }
          }
          LOG.info(msg);
        }
      }
    });

    handler.startNotify();
    handler.waitFor();

    final List<String> errors = messages.get(CompilerMessageCategory.ERROR);

    if (new File(outFile).isFile()) {
      // if compilation finished correctly, show all errors as warnings
      messages.get(CompilerMessageCategory.WARNING).addAll(errors);
      errors.clear();
    }
    else if (errors.size() == 0) {
      errors.add("Cannot create classes.dex file");
    }

    return messages;
  }

  private static boolean hasXmxParam(ParametersList paramList) {
    for (String param : paramList.getParameters()) {
      if (param.startsWith("-Xmx")) {
        return true;
      }
    }
    return false;
  }
}
