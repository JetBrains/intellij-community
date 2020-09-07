// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.nio.charset.Charset;

import static org.jetbrains.plugins.groovy.bundled.BundledGroovy.getBundledGroovyFile;

public class DefaultGroovyScriptRunner extends GroovyScriptRunner {

  @Override
  public boolean isValidModule(@NotNull Module module) {
    return LibrariesUtil.hasGroovySdk(module);
  }

  @Override
  public void ensureRunnerConfigured(@NotNull GroovyScriptRunConfiguration configuration) throws RuntimeConfigurationException {
    Module module = configuration.getModule();
    if (module == null) {
      throw new RuntimeConfigurationException(GroovyBundle.message("script.runner.module.not.specified.message"));
    }

    if (LibrariesUtil.getGroovyHomePath(module) == null) {
      RuntimeConfigurationException e = new RuntimeConfigurationException(
        GroovyBundle.message("script.runner.no.groovy.for.module", module.getName())
      );
      e.setQuickFix(() -> ModulesConfigurator.showDialog(module.getProject(), module.getName(), ClasspathEditor.getName()));
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
      params.getClassPath().add(getBundledGroovyFile());
    }
    else if (groovyJar != null) {
      params.getClassPath().add(groovyJar);
    }

    if (addClasspathToRunner) {
      getClassPathFromRootModel(module, tests, params, true, params.getClassPath());
    }

    setToolsJar(params);

    String groovyHome = useBundled ? FileUtil.toCanonicalPath(getBundledGroovyFile().getParentFile().getParent()) : LibrariesUtil.getGroovyHomePath(module);
    String groovyHomeDependentName = groovyHome != null ? FileUtil.toSystemDependentName(groovyHome) : null;

    if (groovyHomeDependentName != null) {
      setGroovyHome(params, groovyHomeDependentName);
    }

    final String confPath = getConfPath(groovyHomeDependentName);
    if (confPath != null) {
      params.getVMParametersList().add("-Dgroovy.starter.conf=" + confPath);
      params.getProgramParametersList().add("--conf");
      params.getProgramParametersList().add(confPath);
    }

    HttpConfigurable.getInstance().getJvmProperties(false, null).forEach(p -> params.getVMParametersList().addProperty(p.first, p.second));

    params.setMainClass("org.codehaus.groovy.tools.GroovyStarter");

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
