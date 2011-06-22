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
package org.jetbrains.idea.devkit.run;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.projectRoots.Sandbox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PluginRunConfiguration extends RunConfigurationBase implements ModuleRunConfiguration {
  private Module myModule;
  private String myModuleName;

  public String VM_PARAMETERS;
  public String PROGRAM_PARAMETERS;
  @NonNls private static final String NAME = "name";
  @NonNls private static final String MODULE = "module";

  public PluginRunConfiguration(final Project project, final ConfigurationFactory factory, final String name) {
    super(project, factory, name);
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new PluginRunConfigurationEditor(this);
  }

  public JDOMExternalizable createRunnerSettings(ConfigurationInfoProvider provider) {
    return null;
  }

  public SettingsEditor<JDOMExternalizable> getRunnerSettingsEditor(ProgramRunner runner) {
    return null;
  }

  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    if (getModule() == null){
      throw new ExecutionException(DevKitBundle.message("run.configuration.no.module.specified"));
    }
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(getModule());
    final Sdk jdk = rootManager.getSdk();
    if (jdk == null) {
      throw CantRunException.noJdkForModule(getModule());
    }

    final Sdk ideaJdk = IdeaJdk.findIdeaJdk(jdk);
    if (ideaJdk == null) {
      throw new ExecutionException(DevKitBundle.message("jdk.type.incorrect.common"));
    }
    String sandboxHome = ((Sandbox)ideaJdk.getSdkAdditionalData()).getSandboxHome();

    if (sandboxHome == null){
      throw new ExecutionException(DevKitBundle.message("sandbox.no.configured"));
    }

    try {
      sandboxHome = new File(sandboxHome).getCanonicalPath();
    }
    catch (IOException e) {
      throw new ExecutionException(DevKitBundle.message("sandbox.no.configured"));
    }
    final String canonicalSandbox = sandboxHome;

    //copy license from running instance of idea
    IdeaLicenseHelper.copyIDEALicencse(sandboxHome, ideaJdk);

    final JavaCommandLineState state = new JavaCommandLineState(env) {
      protected JavaParameters createJavaParameters() throws ExecutionException {

        final JavaParameters params = new JavaParameters();

        ParametersList vm = params.getVMParametersList();

        fillParameterList(vm, VM_PARAMETERS);
        fillParameterList(params.getProgramParametersList(), PROGRAM_PARAMETERS);
        if (!VM_PARAMETERS.contains("-Xms")) {
          vm.add("-Xms128m");
        }
        if (!VM_PARAMETERS.contains("-Xmx")) {
          vm.add("-Xmx512m");
        }
        if (!VM_PARAMETERS.contains("-XX:MaxPermSize")) {
          vm.add("-XX:MaxPermSize=250m");
        }
        @NonNls String libPath = ideaJdk.getHomePath() + File.separator + "lib";
        vm.add("-Xbootclasspath/a:" + libPath + File.separator + "boot.jar");

        vm.defineProperty("idea.config.path", canonicalSandbox + File.separator + "config");
        vm.defineProperty("idea.system.path", canonicalSandbox + File.separator + "system");
        vm.defineProperty("idea.plugins.path", canonicalSandbox + File.separator + "plugins");

        if (SystemInfo.isMac) {
          vm.defineProperty("idea.smooth.progress", "false");
          vm.defineProperty("apple.laf.useScreenMenuBar", "true");
        }

        params.setWorkingDirectory(ideaJdk.getHomePath() + File.separator + "bin" + File.separator);

        params.setJdk(ideaJdk);

        params.getClassPath().addFirst(libPath + File.separator + "log4j.jar");
        params.getClassPath().addFirst(libPath + File.separator + "jdom.jar");
        params.getClassPath().addFirst(libPath + File.separator + "trove4j.jar");
        params.getClassPath().addFirst(libPath + File.separator + "openapi.jar");
        params.getClassPath().addFirst(libPath + File.separator + "util.jar");
        params.getClassPath().addFirst(libPath + File.separator + "extensions.jar");
        params.getClassPath().addFirst(libPath + File.separator + "bootstrap.jar");
        params.getClassPath().addFirst(libPath + File.separator + "idea.jar");
        params.getClassPath().addFirst(libPath + File.separator + "idea_rt.jar");
        params.getClassPath().addFirst(((JavaSdkType)ideaJdk.getSdkType()).getToolsPath(ideaJdk));

        params.setMainClass("com.intellij.idea.Main");

        return params;
      }
    };

    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject()));
    return state;
  }

  private static void fillParameterList(ParametersList list, String value) {
    final String[] parameters = value != null ? value.split(" ") : null;
    for (int i = 0; parameters != null && i < parameters.length; i++) {
      if (parameters[i] != null && parameters[i].length() > 0){
        list.add(parameters[i]);
      }
    }
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    if (getModule() == null) {
      throw new RuntimeConfigurationException(DevKitBundle.message("run.configuration.no.module.specified"));
    }
    String moduleName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return getModule().getName();
      }
    });
    if (ModuleManager.getInstance(getProject()).findModuleByName(moduleName) == null){
      throw new RuntimeConfigurationException(DevKitBundle.message("run.configuration.no.module.specified"));
    }
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(getModule());
    final Sdk jdk = rootManager.getSdk();
    if (jdk == null) {
      throw new RuntimeConfigurationException(DevKitBundle.message("jdk.no.specified", moduleName));
    }
    if (IdeaJdk.findIdeaJdk(jdk) == null) {
      throw new RuntimeConfigurationException(DevKitBundle.message("jdk.type.incorrect", moduleName));
    }
  }


  @NotNull
  public Module[] getModules() {
    List<Module> modules = new ArrayList<Module>();
    Module[] allModules = ModuleManager.getInstance(getProject()).getModules();
    for (Module module : allModules) {
      if (module.getModuleType() == PluginModuleType.getInstance()) {
        modules.add(module);
      }
    }
    return modules.toArray(new Module[modules.size()]);
  }

  public void readExternal(Element element) throws InvalidDataException {
    Element module = element.getChild(MODULE);
    if (module != null) {
      myModuleName = module.getAttributeValue(NAME);
    }
    DefaultJDOMExternalizer.readExternal(this, element);
    super.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    Element moduleElement = new Element(MODULE);
    moduleElement.setAttribute(NAME, ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        final Module module = getModule();
        return module != null ? module.getName()
                              : myModuleName != null ? myModuleName : "";
      }
    }));
    element.addContent(moduleElement);
    DefaultJDOMExternalizer.writeExternal(this, element);
    super.writeExternal(element);
  }

  @Nullable
  public Module getModule() {
    if (myModule == null && myModuleName != null){
      myModule = ModuleManager.getInstance(getProject()).findModuleByName(myModuleName);
    }
    if (myModule != null && myModule.isDisposed()) {
      myModule = null;
    }

    return myModule;
  }

  public void setModule(Module module) {
    myModule = module;
  }
}
