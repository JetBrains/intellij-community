/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.util.ScriptFileUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.groovy.GroovycOutputParser;
import org.jetbrains.plugins.groovy.config.GroovyFacetUtil;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.nio.charset.Charset;

public class DefaultGroovyScriptRunner extends GroovyScriptRunner {

  @Override
  public boolean isValidModule(@NotNull Module module) {
    return LibrariesUtil.hasGroovySdk(module);
  }

  @Override
  public void ensureRunnerConfigured(@NotNull GroovyScriptRunConfiguration configuration) throws RuntimeConfigurationException {
    Module module = configuration.getModule();
    if (module == null) {
      throw new RuntimeConfigurationException("Module is not specified");
    }

    if (LibrariesUtil.getGroovyHomePath(module) == null) {
      RuntimeConfigurationException e = new RuntimeConfigurationException("Groovy is not configured for module '" + module.getName() + "'");
      e.setQuickFix(() -> ModulesConfigurator.showDialog(module.getProject(), module.getName(), ClasspathEditor.NAME));
      throw e;
    }
  }

  @Override
  public void configureCommandLine(JavaParameters params, @Nullable Module module, boolean tests, VirtualFile script, GroovyScriptRunConfiguration configuration) throws CantRunException {
    configureGenericGroovyRunner(params, module, "groovy.ui.GroovyMain", false, tests, configuration.isAddClasspathToTheRunner(), true);

    //addClasspathFromRootModel(module, tests, params, true);

    params.getVMParametersList().addParametersString(configuration.getVMParameters());

    addScriptEncodingSettings(params, script, module);

    if (configuration.isDebugEnabled()) {
      params.getProgramParametersList().add("--debug");
    }

    String path = ScriptFileUtil.getLocalFilePath(StringUtil.notNullize(configuration.getScriptPath()));
    params.getProgramParametersList().add(FileUtil.toSystemDependentName(path));
    params.getProgramParametersList().addParametersString(configuration.getProgramParameters());
  }

  public static void configureGenericGroovyRunner(@NotNull JavaParameters params,
                                                  @NotNull Module module,
                                                  @NotNull String mainClass,
                                                  boolean useBundled,
                                                  boolean tests) throws CantRunException {
    configureGenericGroovyRunner(params, module, mainClass, useBundled, tests, true, true);
  }

  public static void configureGenericGroovyRunner(@NotNull JavaParameters params,
                                                  @NotNull Module module,
                                                  @NotNull String mainClass,
                                                  boolean useBundled,
                                                  boolean tests,
                                                  boolean addClasspathToRunner,
                                                  boolean addClassPathToStarter) throws CantRunException {
    final VirtualFile groovyJar = findGroovyJar(module);
    if (useBundled) {
      params.getClassPath().add(GroovyFacetUtil.getBundledGroovyJar());
    }
    else if (groovyJar != null) {
      params.getClassPath().add(groovyJar);
    }

    if (addClasspathToRunner) {
      getClassPathFromRootModel(module, tests, params, true, params.getClassPath());
    }

    setToolsJar(params);

    String groovyHome = useBundled ? FileUtil.toCanonicalPath(GroovyFacetUtil.getBundledGroovyJar().getParentFile().getParent()) : LibrariesUtil.getGroovyHomePath(module);
    String groovyHomeDependentName = groovyHome != null ? FileUtil.toSystemDependentName(groovyHome) : null;

    if (groovyHomeDependentName != null) {
      setGroovyHome(params, groovyHomeDependentName);
    }

    final String confPath = getConfPath(groovyHomeDependentName);
    params.getVMParametersList().add("-Dgroovy.starter.conf=" + confPath);
    HttpConfigurable.getInstance().getJvmProperties(false, null).forEach(p -> params.getVMParametersList().addProperty(p.first, p.second));

    params.setMainClass("org.codehaus.groovy.tools.GroovyStarter");

    params.getProgramParametersList().add("--conf");
    params.getProgramParametersList().add(confPath);

    params.getProgramParametersList().add("--main");
    params.getProgramParametersList().add(mainClass);

    if (addClassPathToStarter) {
      addClasspathFromRootModel(module, tests, params, true);
    }

    if (params.getVMParametersList().getPropertyValue(GroovycOutputParser.GRAPE_ROOT) == null) {
      String sysRoot = System.getProperty(GroovycOutputParser.GRAPE_ROOT);
      if (sysRoot != null) {
        params.getVMParametersList().defineProperty(GroovycOutputParser.GRAPE_ROOT, sysRoot);
      }
    }
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
