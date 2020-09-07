// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.gant;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunner;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.io.File;
import java.util.List;

/**
 * @author ilyas
 */
public class GantRunner extends GroovyScriptRunner {

  private static final String UNIQUE_STRING = "d230efbae4b744ae86ef4014eef1b387";

  @Override
  public boolean shouldRefreshAfterFinish() {
    return true;
  }

  @Override
  public boolean isValidModule(@NotNull Module module) {
    return GantUtils.isSDKConfiguredToRun(module);
  }

  @Override
  public void ensureRunnerConfigured(@NotNull GroovyScriptRunConfiguration configuration) throws RuntimeConfigurationException {
    Project project = configuration.getProject();
    if (GantUtils.getSDKInstallPath(configuration.getModule(), project).isEmpty()) {
      RuntimeConfigurationException e = new RuntimeConfigurationException(GroovyBundle.message("dialog.message.gant.not.configured"));
      e.setQuickFix(() -> ShowSettingsUtil.getInstance().editConfigurable(project, new GantConfigurable(project)));
      throw e;
    }
  }

  @Nullable
  private static String getGantConfPath(final String gantHome) {
    String confPath = FileUtil.toSystemDependentName(gantHome + "/conf/gant-starter.conf");
    if (new File(confPath).exists()) {
      return confPath;
    }

    return getConfPath(gantHome);
  }

  @Override
  public void configureCommandLine(JavaParameters params, @Nullable Module module, boolean tests, VirtualFile script, GroovyScriptRunConfiguration configuration) throws CantRunException {
    String gantHome = GantUtils.getSDKInstallPath(module, configuration.getProject());

    addGroovyAndAntJars(params, module, gantHome);

    setToolsJar(params);

    setGroovyHome(params, gantHome);

    final String confPath = getGantConfPath(gantHome);
    if (confPath != null) {
      params.getVMParametersList().add("-Dgroovy.starter.conf=" + confPath);
      params.getProgramParametersList().add("--conf");
      params.getProgramParametersList().add(confPath);
    }

    params.getVMParametersList().addParametersString(configuration.getVMParameters());
    params.setMainClass("org.codehaus.groovy.tools.GroovyStarter");

    if (gantHome.contains("grails")) {
      params.getClassPath().addAllFiles(GroovyUtils.getFilesInDirectoryByPattern(gantHome + "/lib", ".*\\.jar"));
    }

    addClasspathFromRootModel(module, tests, params, false);

    String antHome = System.getenv("ANT_HOME");
    if (StringUtil.isEmpty(antHome)) {
      antHome = gantHome;
    }

    params.getVMParametersList().add("-Dant.home=" + antHome);
    params.getVMParametersList().add("-Dgant.home=" + gantHome);

    params.getProgramParametersList().add("--main");
    params.getProgramParametersList().add("gant.Gant");

    params.getProgramParametersList().add("--file");
    params.getProgramParametersList().add(FileUtil.toSystemDependentName(configuration.getScriptPath()));

    if (configuration.isDebugEnabled()) {
      params.getProgramParametersList().add("--debug");
      params.getProgramParametersList().add("-D" + UNIQUE_STRING);
    }

    params.getProgramParametersList().addParametersString(configuration.getProgramParameters());
  }

  private static void addGroovyAndAntJars(JavaParameters params, Module module, String gantHome) {
    final File[] groovyJars = GroovyConfigUtils.getGroovyAllJars(gantHome + "/lib/");
    if (groovyJars.length > 0) {
      params.getClassPath().add(groovyJars[0].getAbsolutePath());
    }

    if (module == null) {
      return;
    }

    final String groovyHome = LibrariesUtil.getGroovyHomePath(module);
    if (groovyHome != null) {
      File[] libJars = GroovyUtils.getFilesInDirectoryByPattern(groovyHome + "/lib/", ".*\\.jar");
      if (libJars.length > 0) {
        params.getClassPath().addAllFiles(libJars);
      }
    }

    List<VirtualFile> classpath = params.getClassPath().getRootDirs();

    String[] characteristicClasses = ContainerUtil.ar(
      LibrariesUtil.SOME_GROOVY_CLASS, "org.apache.tools.ant.BuildException", "org.apache.tools.ant.launch.AntMain",
      "org.apache.commons.cli.ParseException");
    for (String someClass : characteristicClasses) {
      if (!LibraryUtil.isClassAvailableInLibrary(classpath, someClass)) {
        VirtualFile jar = LibrariesUtil.findJarWithClass(module, someClass);
        if (jar != null) {
          params.getClassPath().add(jar);
        }
      }
    }
  }
}
