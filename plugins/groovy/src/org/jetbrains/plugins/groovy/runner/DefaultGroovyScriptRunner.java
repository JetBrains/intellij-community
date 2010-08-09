/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.project.Project;
import com.intellij.util.ObjectUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

public class DefaultGroovyScriptRunner extends GroovyScriptRunner {

  @Override
  public boolean isValidModule(@NotNull Module module) {
    return LibrariesUtil.hasGroovySdk(module);
  }

  @Override
  public boolean ensureRunnerConfigured(@Nullable Module module, RunProfile profile, Executor executor, final Project project) throws ExecutionException {
    if (module == null) {
      throw new ExecutionException("Module is not specified");
    }

    if (LibrariesUtil.getGroovyHomePath(module) == null) {
      ExecutionUtil.handleExecutionError(project, executor.getToolWindowId(), profile, new ExecutionException("Groovy is not configured"));
      ModulesConfigurator.showDialog(module.getProject(), module.getName(), ClasspathEditor.NAME, false);
      return false;
    }


    return true;
  }

  @Override
  public void configureCommandLine(JavaParameters params, @Nullable Module module, boolean tests, VirtualFile script, GroovyScriptRunConfiguration configuration) throws CantRunException {
    configureGenericGroovyRunner(params, module, tests, "groovy.ui.GroovyMain");

    addClasspathFromRootModel(module, tests, params, true);

    params.getVMParametersList().addParametersString(configuration.vmParams);

    params.getProgramParametersList().add(FileUtil.toSystemDependentName(configuration.scriptPath));
    params.getProgramParametersList().addParametersString(configuration.scriptParams);

    addScriptEncodingSettings(params, script, module);

    if (configuration.isDebugEnabled) {
      params.getProgramParametersList().add("--debug");
    }
  }

  public static void configureGenericGroovyRunner(JavaParameters params, Module module, boolean tests, String mainClass) throws CantRunException {
    assert module != null;
    final VirtualFile groovyJar = findGroovyJar(module);
    if (groovyJar != null) {
      params.getClassPath().add(groovyJar);
    }

    setToolsJar(params);

    final String groovyHome = FileUtil.toSystemDependentName(ObjectUtils.assertNotNull(LibrariesUtil.getGroovyHomePath(module)));
    setGroovyHome(params, groovyHome);

    final String confPath = getConfPath(groovyHome);
    params.getVMParametersList().add("-Dgroovy.starter.conf=" + confPath);

    params.setMainClass("org.codehaus.groovy.tools.GroovyStarter");

    params.getProgramParametersList().add("--conf");
    params.getProgramParametersList().add(confPath);

    params.getProgramParametersList().add("--main");
    params.getProgramParametersList().add(mainClass);
  }

  private static void addScriptEncodingSettings(final JavaParameters params, final VirtualFile scriptFile, Module module) {
    Charset charset = EncodingProjectManager.getInstance(module.getProject()).getEncoding(scriptFile, true);
    if (charset == null) {
      charset = EncodingManager.getInstance().getDefaultCharset();
      if (!Comparing.equal(CharsetToolkit.getDefaultSystemCharset(), charset)) {
        params.getProgramParametersList().add("--encoding=" + charset.displayName());
      }
    }
    else {
      params.getProgramParametersList().add("--encoding=" + charset.displayName());
    }
  }

}
