/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.mvc;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * @author peter
 */
public abstract class MvcRunConfiguration extends ModuleBasedConfiguration<RunConfigurationModule> implements
                                                                                                   CommonJavaRunConfigurationParameters {
  public String vmParams;
  public String cmdLine;
  public boolean depsClasspath = true;
  protected final MvcFramework myFramework;
  public final Map<String, String> envs = new HashMap<>();
  public boolean passParentEnv = true;

  public MvcRunConfiguration(final String name, final RunConfigurationModule configurationModule, final ConfigurationFactory factory, MvcFramework framework) {
    super(name, configurationModule, factory);
    myFramework = framework;
  }

  public MvcFramework getFramework() {
    return myFramework;
  }

  @Override
  public String getVMParameters() {
    return vmParams;
  }

  @Override
  public void setVMParameters(String vmParams) {
    this.vmParams = vmParams;
  }

  @Override
  public void setProgramParameters(@Nullable String value) {
    cmdLine = value;
  }

  @Override
  @Nullable
  public String getProgramParameters() {
    return cmdLine;
  }

  @Override
  public void setWorkingDirectory(@Nullable String value) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public String getWorkingDirectory() {
    return null;
  }

  @Override
  public void setEnvs(@NotNull Map<String, String> envs) {
    this.envs.clear();
    this.envs.putAll(envs);
  }

  @Override
  @NotNull
  public Map<String, String> getEnvs() {
    return envs;
  }

  @Override
  public void setPassParentEnvs(boolean passParentEnv) {
    this.passParentEnv = passParentEnv;
  }

  @Override
  public boolean isPassParentEnvs() {
    return passParentEnv;
  }

  @Override
  public boolean isAlternativeJrePathEnabled() {
    return false;
  }

  @Override
  public void setAlternativeJrePathEnabled(boolean enabled) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public String getAlternativeJrePath() {
    return null;
  }

  @Override
  public void setAlternativeJrePath(String path) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public String getRunClass() {
    return null;
  }

  @Override
  @Nullable
  public String getPackage() {
    return null;
  }


  @Override
  public Collection<Module> getValidModules() {
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    ArrayList<Module> res = new ArrayList<>();
    for (Module module : modules) {
      if (isSupport(module)) {
        res.add(module);
      }
    }
    return res;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    PathMacroManager.getInstance(getProject()).expandPaths(element);
    super.readExternal(element);
    readModule(element);
    vmParams = JDOMExternalizer.readString(element, "vmparams");
    cmdLine = JDOMExternalizer.readString(element, "cmdLine");

    String sPassParentEnviroment = JDOMExternalizer.readString(element, "passParentEnv");
    passParentEnv = StringUtil.isEmpty(sPassParentEnviroment) || Boolean.parseBoolean(sPassParentEnviroment);

    envs.clear();
    JDOMExternalizer.readMap(element, envs, null, "env");

    JavaRunConfigurationExtensionManager.getInstance().readExternal(this, element);

    depsClasspath = !"false".equals(JDOMExternalizer.readString(element, "depsClasspath"));
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    writeModule(element);
    JDOMExternalizer.write(element, "vmparams", vmParams);
    JDOMExternalizer.write(element, "cmdLine", cmdLine);
    JDOMExternalizer.write(element, "depsClasspath", depsClasspath);
    JDOMExternalizer.writeMap(element, envs, null, "env");
    JDOMExternalizer.write(element, "passParentEnv", passParentEnv);

    JavaRunConfigurationExtensionManager.getInstance().writeExternal(this, element);

  }

  protected abstract String getNoSdkMessage();

  protected boolean isSupport(@NotNull Module module) {
    return myFramework.getSdkRoot(module) != null && !myFramework.isAuxModule(module);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    final Module module = getModule();
    if (module == null) {
      throw new RuntimeConfigurationException("Module not specified");
    }
    if (module.isDisposed()) {
      throw new RuntimeConfigurationException("Module is disposed");
    }
    if (!isSupport(module)) {
      throw new RuntimeConfigurationException(getNoSdkMessage());
    }
    super.checkConfiguration();
  }

  @Nullable
  public Module getModule() {
    return getConfigurationModule().getModule();
  }

  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
    final Module module = getModule();
    if (module == null) {
      throw new ExecutionException("Module is not specified");
    }

    if (!isSupport(module)) {
      throw new ExecutionException(getNoSdkMessage());
    }

    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    final Sdk sdk = rootManager.getSdk();
    if (sdk == null || !(sdk.getSdkType() instanceof JavaSdkType)) {
      throw CantRunException.noJdkForModule(module);
    }

    return createCommandLineState(environment, module);

  }

  protected RunProfileState createCommandLineState(@NotNull ExecutionEnvironment environment, @NotNull Module module) {
    return new MvcCommandLineState(environment, cmdLine, module, false);
  }

  @Override
  @NotNull
  public SettingsEditor<? extends MvcRunConfiguration> getConfigurationEditor() {
    return new MvcRunConfigurationEditor<>();
  }

  public class MvcCommandLineState extends JavaCommandLineState {
    protected final boolean myForTests;

    protected String myCmdLine;

    protected final @NotNull Module myModule;

    public MvcCommandLineState(@NotNull ExecutionEnvironment environment, String cmdLine, @NotNull Module module, boolean forTests) {
      super(environment);
      myModule = module;
      myForTests = forTests;
      myCmdLine = cmdLine;
    }

    public String getCmdLine() {
      return myCmdLine;
    }

    public void setCmdLine(String cmdLine) {
      myCmdLine = cmdLine;
    }

    protected void addEnvVars(final JavaParameters params) {
      Map<String, String> envVars = new HashMap<>(envs);
      envVars.putAll(params.getEnv());
      
      params.setupEnvs(envVars, passParentEnv);
      
      MvcFramework.addJavaHome(params, myModule);
    }

    @NotNull
    @Override
    protected OSProcessHandler startProcess() throws ExecutionException {
      OSProcessHandler handler = super.startProcess();
      handler.setShouldDestroyProcessRecursively(true);
      final RunnerSettings runnerSettings = getRunnerSettings();
      JavaRunConfigurationExtensionManager.getInstance().attachExtensionsToProcess(MvcRunConfiguration.this, handler, runnerSettings);
      return handler;
    }

    @Override
    protected final JavaParameters createJavaParameters() throws ExecutionException {
      JavaParameters javaParameters = createJavaParametersMVC();
      for(RunConfigurationExtension ext: Extensions.getExtensions(RunConfigurationExtension.EP_NAME)) {
        ext.updateJavaParameters(MvcRunConfiguration.this, javaParameters, getRunnerSettings());
      }

      return javaParameters;
    }

    protected JavaParameters createJavaParametersMVC() throws ExecutionException {
      MvcCommand cmd = MvcCommand.parse(myCmdLine).setVmOptions(vmParams);

      final JavaParameters params = myFramework.createJavaParameters(myModule, false, myForTests, depsClasspath, cmd);

      addEnvVars(params);

      return params;
    }

  }

}
