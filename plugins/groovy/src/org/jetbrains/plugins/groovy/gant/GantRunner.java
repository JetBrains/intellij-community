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
package org.jetbrains.plugins.groovy.gant;

import com.intellij.execution.CantRunException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunner;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.io.File;

/**
 * @author ilyas
 */
public class GantRunner extends GroovyScriptRunner {

  @Override
  public boolean isValidModule(@NotNull Module module) {
    return GantUtils.isSDKConfiguredToRun(module);
  }

  @Override
  public boolean ensureRunnerConfigured(@Nullable Module module, RunProfile profile, Executor executor, final Project project) {
    if (!(GantUtils.getSDKInstallPath(module, project).length() > 0)) {
      int result = Messages
        .showOkCancelDialog("Gant is not configured. Do you want to configure it?", "Configure Gant SDK",
                            GantIcons.GANT_ICON_16x16);
      if (result == 0) {
        final ShowSettingsUtil util = ShowSettingsUtil.getInstance();
        util.editConfigurable(project, util.createProjectConfigurable(project, GantConfigurable.class));
      }
      if (!(GantUtils.getSDKInstallPath(module, project).length() > 0)) {
        return false;
      }
    }

    return true;
  }

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

    final File[] groovyJars = GroovyUtils.getFilesInDirectoryByPattern(gantHome + "/lib/", GroovyConfigUtils.GROOVY_ALL_JAR_PATTERN);
    if (groovyJars.length > 0) {
      params.getClassPath().add(groovyJars[0].getAbsolutePath());
    } else if (module != null) {
      final String groovyHome = LibrariesUtil.getGroovyHomePath(module);
      if (groovyHome != null) {
        for (File groovyLibJar : GroovyUtils.getFilesInDirectoryByPattern(groovyHome + "/lib/", ".*\\.jar")) {
          params.getClassPath().add(groovyLibJar);
        }
      }
    }


    setToolsJar(params);

    setGroovyHome(params, gantHome);

    final String confPath = getGantConfPath(gantHome);
    params.getVMParametersList().add("-Dgroovy.starter.conf=" + confPath);

    params.getVMParametersList().addParametersString(configuration.vmParams);
    params.setMainClass("org.codehaus.groovy.tools.GroovyStarter");

    params.getProgramParametersList().add("--conf");
    params.getProgramParametersList().add(confPath);

    if (gantHome.contains("grails")) {
      params.getClassPath().addAllFiles(GroovyUtils.getFilesInDirectoryByPattern(gantHome + "/lib", ".*\\.jar"));
    }

    addClasspathFromRootModel(module, tests, params, false);

    String antHome = System.getenv("ANT_HOME");
    if (StringUtil.isEmpty(antHome)) {
      antHome = gantHome;
    }

    params.getVMParametersList().add("-Dant.home=" + antHome);
    params.getVMParametersList().add("-Dgant.home=" + gantHome + "");

    params.getProgramParametersList().add("--main");
    params.getProgramParametersList().add("gant.Gant");

    params.getProgramParametersList().add("--file");
    params.getProgramParametersList().add(FileUtil.toSystemDependentName(configuration.scriptPath));

    params.getProgramParametersList().addParametersString(configuration.scriptParams);

    if (configuration.isDebugEnabled) {
      params.getProgramParametersList().add("--debug");
    }
  }

}
