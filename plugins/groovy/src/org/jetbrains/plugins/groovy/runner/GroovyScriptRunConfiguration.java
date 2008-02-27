/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.SystemInfo;
import org.jdom.Element;
import org.jetbrains.plugins.groovy.config.GroovyFacet;
import org.jetbrains.plugins.groovy.config.GroovyGrailsConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class GroovyScriptRunConfiguration extends ModuleBasedConfiguration {
  private GroovyScriptConfigurationFactory factory;
  public String vmParams;
  public String scriptParams;
  public String scriptPath;
  public String workDir = ".";
  public final String GROOVY_STARTER = "org.codehaus.groovy.tools.GroovyStarter";
  public final String GROOVY_MAIN = "groovy.ui.GroovyMain";

  public GroovyScriptRunConfiguration(GroovyScriptConfigurationFactory factory, Project project, String name) {
    super(name, new RunConfigurationModule(project, true), factory);
    this.factory = factory;
  }

  public Collection<Module> getValidModules() {
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    ArrayList<Module> res = new ArrayList<Module>();
    for (Module module : modules)
      if (FacetManager.getInstance(module).getFacetsByType(GroovyFacet.ID).size() > 0)
        res.add(module);
    return res;
  }

  public void setAbsoluteWorkDir(String dir) {
    workDir = dir;
  }

  public String getAbsoluteWorkDir() {
    return new File(workDir).getAbsolutePath();
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    readModule(element);
    scriptPath = JDOMExternalizer.readString(element, "path");
    vmParams = JDOMExternalizer.readString(element, "vmparams");
    scriptParams = JDOMExternalizer.readString(element, "params");
    workDir = JDOMExternalizer.readString(element, "workDir");
    if (!new File(workDir).isAbsolute()) { //was stored as relative path, so try to make it absolute here
      workDir = new File(getProject().getBaseDir().getPath(), workDir).getAbsolutePath();
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    writeModule(element);
    JDOMExternalizer.write(element, "path", scriptPath);
    JDOMExternalizer.write(element, "vmparams", vmParams);
    JDOMExternalizer.write(element, "params", scriptParams);
    JDOMExternalizer.write(element, "workDir", workDir);
  }

  protected ModuleBasedConfiguration createInstance() {
    return new GroovyScriptRunConfiguration(factory, getConfigurationModule().getProject(), getName());
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new GroovyRunConfigurationEditor(getProject());
  }

  public RunProfileState getState(
      DataContext context,
      RunnerInfo runnerInfo,
      RunnerSettings runnerSettings,
      ConfigurationPerRunnerSettings configurationSettings) throws ExecutionException {
    GroovyGrailsConfiguration groovyConfig = GroovyGrailsConfiguration.getInstance();
    if (!groovyConfig.isGroovyConfigured(null)) {
      throw new ExecutionException("Groovy is not configured");
    }

    final Module module = getModule();
    if (module == null) {
      throw new ExecutionException("Module is not specified");
    }

    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    final ProjectJdk jdk = rootManager.getJdk();
    if (jdk == null) {
      throw CantRunException.noJdkForModule(getModule());
    }

    final JavaCommandLineState state = new JavaCommandLineState(runnerSettings, configurationSettings) {
      protected JavaParameters createJavaParameters() throws ExecutionException {
        JavaParameters params = new JavaParameters();

        configureJavaParams(params, module);
        configureGroovyStarter(params);
        configureScript(params);

        return params;
      }
    };

    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject()));
    state.setModulesToCompile(getModules());
    return state;
  }

  private void configureJavaParams(JavaParameters params, Module module) throws CantRunException {

    params.configureByModule(module, JavaParameters.JDK_AND_CLASSES);
    params.setWorkingDirectory(getAbsoluteWorkDir());

    GroovyGrailsConfiguration config = GroovyGrailsConfiguration.getInstance();

    //add starter configuration parameters
    String groovyHome = config.getGroovyInstallPath();
    if (groovyHome != null) {
      params.getVMParametersList().addParametersString("-Dgroovy.home=" + "\"" + groovyHome + "\"");
    }

    // add user parameters
    params.getVMParametersList().addParametersString(vmParams);

    // set starter class
    params.setMainClass(GROOVY_STARTER);
  }

  private void configureGroovyStarter(JavaParameters params) {
    // add GroovyStarter parameters
    params.getProgramParametersList().add("--main");
    params.getProgramParametersList().add(GROOVY_MAIN);

    params.getProgramParametersList().add("--classpath");

    // Clear module libraries from JDK's occurrences
    List<String> list = params.getClassPath().getPathList();
    ProjectJdk jdk = params.getJdk();
    StringBuffer buffer = new StringBuffer();
    if (jdk != null) {

      String jdkLibDir = getJdkLibDir(jdk);

      for (String libPath : list) {
        if (!libPath.contains(jdkLibDir)) {
          buffer.append(libPath).append(File.pathSeparator);
        }
      }
    }

    params.getProgramParametersList().add("\"" + workDir + File.pathSeparator + buffer.toString() + "\"");
    params.getProgramParametersList().add("--debug");
  }

  private String getJdkLibDir(ProjectJdk jdk) {
    String jdkLibDir;
    if (SystemInfo.isMac) {
      String path = jdk.getBinPath();
      int index = path.indexOf("Home/bin");
      assert index > 0; 
      path = path.substring(0, index) + File.separator + "Classes";
      jdkLibDir = new File(path).getAbsolutePath();
    } else {
      String path = jdk.getRtLibraryPath();
      jdkLibDir = new File(path).getParentFile().getAbsolutePath();
    }
    return jdkLibDir;
  }

  private void configureScript(JavaParameters params) {
    // add script
    params.getProgramParametersList().add(scriptPath);

    // add script parameters
    params.getProgramParametersList().addParametersString(scriptParams);
  }

  public Module getModule() {
    return getConfigurationModule().getModule();
  }
}
