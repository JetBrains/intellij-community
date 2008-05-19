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
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.config.GroovyFacet;
import org.jetbrains.plugins.groovy.util.GroovyUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarFile;

class GroovyScriptRunConfiguration extends ModuleBasedConfiguration {
  private GroovyScriptConfigurationFactory factory;
  public String vmParams;
  public boolean isDebugEnabled;
  public String scriptParams;
  public String scriptPath;
  public String workDir = ".";
  public final String GROOVY_STARTER = "org.codehaus.groovy.tools.GroovyStarter";
  public final String GROOVY_MAIN = "groovy.ui.GroovyMain";
  public final String JAVA_LANG_STRING = "java/lang/String.class";

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

  public void setWorkDir(String dir) {
    workDir = dir;
  }

  public String getWorkDir() {
    return workDir;
  }

  public String getAbsoluteWorkDir() {
    if (!new File(workDir).isAbsolute()) {
      return new File(getProject().getLocation(), workDir).getAbsolutePath();
    }
    return workDir;
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    readModule(element);
    scriptPath = JDOMExternalizer.readString(element, "path");
    vmParams = JDOMExternalizer.readString(element, "vmparams");
    scriptParams = JDOMExternalizer.readString(element, "params");
    workDir = JDOMExternalizer.readString(element, "workDir");
    isDebugEnabled = Boolean.parseBoolean(JDOMExternalizer.readString(element, "debug"));
    workDir = getWorkDir();
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    writeModule(element);
    JDOMExternalizer.write(element, "path", scriptPath);
    JDOMExternalizer.write(element, "vmparams", vmParams);
    JDOMExternalizer.write(element, "params", scriptParams);
    JDOMExternalizer.write(element, "workDir", workDir);
    JDOMExternalizer.write(element, "debug", isDebugEnabled);
  }

  protected ModuleBasedConfiguration createInstance() {
    return new GroovyScriptRunConfiguration(factory, getConfigurationModule().getProject(), getName());
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new GroovyRunConfigurationEditor();
  }

  public RunProfileState getState(
      DataContext context,
      RunnerInfo runnerInfo,
      RunnerSettings runnerSettings,
      ConfigurationPerRunnerSettings configurationSettings) throws ExecutionException {
    final Module module = getModule();
    if (module == null) {
      throw new ExecutionException("Module is not specified");
    }

    if (!GroovyConfigUtils.isGroovyConfigured(module)) {
      throw new ExecutionException("Groovy is not configured");
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

    //add starter configuration parameters
    String groovyHome = GroovyConfigUtils.getGroovyInstallPath(module);
    params.getVMParametersList().addParametersString("-Dgroovy.home=" + "\"" + groovyHome + "\"");

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
      String jdkDir = GroovyUtils.getJdkLibDirParent(jdk);
      for (String libPath : list) {
        JarFile jarFile;
        try {
          jarFile = new JarFile(libPath);
        } catch (IOException e) {
          jarFile = null;
        }
        if (jarFile != null && jarFile.getJarEntry(JAVA_LANG_STRING) != null) continue;
        if (!libPath.startsWith(jdkDir)) {
          buffer.append(libPath).append(File.pathSeparator);
        }
      }
    }

    params.getProgramParametersList().add("\"" + workDir + File.pathSeparator + buffer.toString() + "\"");
    if (isDebugEnabled) {
      params.getProgramParametersList().add("--debug");
    }
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
