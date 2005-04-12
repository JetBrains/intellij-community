/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package org.jetbrains.idea.devkit.run;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuidlerFactory;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.*;
import org.jdom.Element;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.projectRoots.Sandbox;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PluginRunConfiguration extends RunConfigurationBase {
  private Module myModule;
  private String myModuleName;

  public String VM_PARAMETERS;

  public PluginRunConfiguration(final Project project, final ConfigurationFactory factory, final String name) {
    super(project, factory, name);
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new PluginRunConfigurationEditor(this);
  }

  public JDOMExternalizable createRunnerSettings(ConfigurationInfoProvider provider) {
    return null;
  }

  public SettingsEditor<JDOMExternalizable> getRunnerSettingsEditor(JavaProgramRunner runner) {
    return null;
  }

  public RunProfileState getState(DataContext context,
                                  RunnerInfo runnerInfo,
                                  RunnerSettings runnerSettings,
                                  ConfigurationPerRunnerSettings configurationSettings) throws ExecutionException {
    if (getModule() == null){
      throw new ExecutionException("No module specified for configuration");
    }
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(getModule());
    final ProjectJdk jdk = rootManager.getJdk();
    if (jdk == null) {
      throw CantRunException.noJdkForModule(getModule());
    }
    if (!(jdk.getSdkType() instanceof IdeaJdk)) {
      throw new ExecutionException("Wrong jdk type for plugin module");
    }
    final String sandboxHome = ((Sandbox)jdk.getSdkAdditionalData()).getSandboxHome();

    //copy license from running instance of idea
    IdeaLicenseHelper.copyIDEALicencse(sandboxHome, jdk);

    final JavaCommandLineState state = new JavaCommandLineState(runnerSettings, configurationSettings) {
      protected JavaParameters createJavaParameters() throws ExecutionException {

        final JavaParameters params = new JavaParameters();

        ParametersList vm = params.getVMParametersList();

        final String[] userVMOptions = VM_PARAMETERS != null ? VM_PARAMETERS.split(" ") : null;
        for (int i = 0; userVMOptions != null && i < userVMOptions.length; i++) {
          if (userVMOptions[i] != null && userVMOptions[i].length() > 0){
            vm.add(userVMOptions[i]);
          }
        }

        String libPath = jdk.getHomePath() + File.separator + "lib";
        vm.add("-Xbootclasspath/p:" + libPath + File.separator + "boot.jar");

        vm.defineProperty("idea.config.path", sandboxHome + File.separator + "config");
        vm.defineProperty("idea.system.path", sandboxHome + File.separator + "system");
        vm.defineProperty("idea.plugins.path", sandboxHome + File.separator + "plugins");

        if (SystemInfo.isMac) {
          vm.defineProperty("idea.smooth.progress", "false");
          vm.defineProperty("apple.laf.useScreenMenuBar", "true");
        }

        params.setWorkingDirectory(jdk.getHomePath() + File.separator + "bin" + File.separator);

        params.setJdk(jdk);

        params.getClassPath().addFirst(libPath + File.separator + "log4j.jar");
        params.getClassPath().addFirst(libPath + File.separator + "openapi.jar");
        params.getClassPath().addFirst(libPath + File.separator + "extensions.jar");
        params.getClassPath().addFirst(libPath + File.separator + "idea.jar");

        params.setMainClass("com.intellij.idea.Main");

        return params;
      }
    };

    state.setConsoleBuilder(TextConsoleBuidlerFactory.getInstance().createBuilder(getProject()));
    state.setModulesToCompile(getModules());    //todo
    return state;
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    if (getModule() == null) {
      throw new RuntimeConfigurationException("Plugin module not specified.");
    }
    String moduleName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return getModule().getName();
      }
    });
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(getModule());
    final ProjectJdk jdk = rootManager.getJdk();
    if (jdk == null) {
      throw new RuntimeConfigurationException("No jdk specified for plugin module \'" + moduleName + "\'");
    }
    if (!(jdk.getSdkType() instanceof IdeaJdk)) {
      throw new RuntimeConfigurationException("Wrong jdk type for plugin module \'" + moduleName + "\'");
    }
  }


  public Module[] getModules() {
    List<Module> modules = new ArrayList<Module>();
    Module[] allModules = ModuleManager.getInstance(getProject()).getModules();
    for (int i = 0; i < allModules.length; i++) {
      Module module = allModules[i];
      if (module.getModuleType() == PluginModuleType.getInstance()) {
        modules.add(module);
      }
    }
    return modules.toArray(new Module[modules.size()]);
  }

  public void readExternal(Element element) throws InvalidDataException {
    Element module = element.getChild("module");
    if (module != null) {
      myModuleName = module.getAttributeValue("name");
    }
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    Element moduleElement = new Element("module");
    moduleElement.setAttribute("name", ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return getModule() != null ? getModule().getName() : "";
      }
    }));
    element.addContent(moduleElement);
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public Module getModule() {
    if (myModule == null && myModuleName != null){
      myModule = ModuleManager.getInstance(getProject()).findModuleByName(myModuleName);
    }
    return myModule;
  }

  public void setModule(Module module) {
    myModule = module;
  }
}