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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.sandbox.Sandbox;
import org.jetbrains.idea.devkit.sandbox.SandboxManager;

import java.util.ArrayList;
import java.util.List;

public class PluginRunConfiguration extends RunConfigurationBase {
  private List<String> myModuleNames = new ArrayList<String>();
  private String mySandboxName = "";

  private String getSandboxPath() {
    return getSandbox().getSandboxHome();
  }

  private String getBasePath() {
    return getSandbox().getIdeaHome();
  }

  public Sandbox getSandbox() {
    return SandboxManager.getInstance().findByName(mySandboxName);
  }

  public void setSandbox(Sandbox box) {
    mySandboxName = box == null ? "" : box.getName();
  }

  public static final String INTERNAL_BUILD_MARK = "__BUILD_NUMBER__";

  public PluginRunConfiguration(final Project project, final ConfigurationFactory factory, final String name) {
    super(project, factory, name);
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new PluginRunConfigurationEditor();
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
    final JavaCommandLineState state = new JavaCommandLineState(runnerSettings, configurationSettings) {
      protected JavaParameters createJavaParameters() throws ExecutionException {
        if (getSandbox() == null){
          throw new CantRunException("No sandbox specified");
        }

        Module[] modules = getModules();
        if (modules.length == 0) {
          throw new CantRunException("No plugin modules selected");
        }

        final JavaParameters params = new JavaParameters();

        ParametersList vm = params.getVMParametersList();

        String libPath = getBasePath() + "/lib";
        vm.add("-Xbootclasspath/p:" + libPath + "/boot.jar");

        vm.defineProperty("idea.config.path", getSandboxPath() + "/config");
        vm.defineProperty("idea.system.path", getSandboxPath() + "/system");
        vm.defineProperty("idea.plugins.path", getSandboxPath() + "/plugins");

        if (SystemInfo.isMac) {
          vm.defineProperty("idea.smooth.progress", "false");
          vm.defineProperty("apple.laf.useScreenMenuBar", "true");
        }

        params.setWorkingDirectory(getBasePath() + "/bin/");



        //TODO: Should run against same JRE IDEA runs against, right?
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(modules[0]);
        final ProjectJdk jdk = rootManager.getJdk();
        if (jdk == null) {
          throw CantRunException.noJdkForModule(modules[0]);
        }
        params.setJdk(jdk);

        params.getClassPath().addFirst(libPath + "/log4j.jar");
        params.getClassPath().addFirst(libPath + "/openapi.jar");
        params.getClassPath().addFirst(libPath + "/idea.jar");

        params.setMainClass("com.intellij.idea.Main");

        return params;
      }
    };

    state.setConsoleBuilder(TextConsoleBuidlerFactory.getInstance().createBuilder(getProject()));
    state.setModulesToCompile(getModules());
    return state;
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    if (getSandbox() == null){
      throw new RuntimeConfigurationException("No sandbox specified");
    }
    /*
    final Module module = getModule();
    if (module != null) {
      if (module.getModuleType() != PluginModuleType.getInstance()) {
        throw new RuntimeConfigurationError("Module " + module.getName() + " is of wrong type. Should be 'Plugin Module'.");
      }

      if (ModuleRootManager.getInstance(module).getJdk() == null) {
        throw new RuntimeConfigurationWarning("No JDK specified for module \"" + module.getName() + "\"");
      }
      else {
        return;
      }
    }
    else {
      if (MODULE_NAME == null || MODULE_NAME.trim().length() == 0) {
        throw new RuntimeConfigurationError("Module not specified");
      }
      else {
        throw new RuntimeConfigurationError("Module \"" + MODULE_NAME + "\" doesn't exist in project");
      }
    }
    */
  }

  public void setModules(Module[] modules) {
    myModuleNames.clear();
    for (int i = 0; i < modules.length; i++) {
      myModuleNames.add(modules[i].getName());
    }
  }

  public Module[] getModules() {
    List<Module> modules = new ArrayList<Module>();
    Module[] allModules = ModuleManager.getInstance(getProject()).getModules();
    for (int i = 0; i < allModules.length; i++) {
      Module module = allModules[i];
      if (module.getModuleType() == PluginModuleType.getInstance() && myModuleNames.contains(module.getName())) {
        modules.add(module);
      }
    }
    return modules.toArray(new Module[modules.size()]);
  }

  public void readExternal(Element element) throws InvalidDataException {
    Element sandbox = element.getChild("sandbox");
    mySandboxName = sandbox == null ? "" : sandbox.getAttributeValue("name");
    List children = element.getChildren("module");
    for (int i = 0; i < children.size(); i++) {
      Element moduleElement = (Element)children.get(i);
      myModuleNames.add(moduleElement.getAttributeValue("name"));
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    Element sandbox = new Element("sandbox");
    sandbox.setAttribute("name", mySandboxName);
    element.addContent(sandbox);
    for (int i = 0; i < myModuleNames.size(); i++) {
      Element moduleElement = new Element("module");
      moduleElement.setAttribute("name", myModuleNames.get(i));
      element.addContent(moduleElement);
    }
  }
}