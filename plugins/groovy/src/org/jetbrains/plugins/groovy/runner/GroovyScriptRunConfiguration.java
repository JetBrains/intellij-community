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
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.jetbrains.plugins.groovy.config.GroovyFacet;

import java.nio.charset.Charset;

public class GroovyScriptRunConfiguration extends AbstractGroovyScriptRunConfiguration {
  private static final String GROOVY_MAIN = "groovy.ui.GroovyMain";

  public GroovyScriptRunConfiguration(ConfigurationFactory factory, Project project, String name) {
    super(name, project, factory);
  }

  @Override
  protected boolean isValidModule(Module module) {
    return GroovyFacet.getInstance(module) != null;
  }

  protected ModuleBasedConfiguration createInstance() {
    return new GroovyScriptRunConfiguration(getFactory(), getConfigurationModule().getProject(), getName());
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new GroovyRunConfigurationEditor();
  }

  @Override
  protected void configureCommandLine(JavaParameters params, Module module, boolean tests, VirtualFile script, String confPath,
                                      final String groovyHome) throws CantRunException {
    defaultGroovyStarter(params, module, confPath, tests, groovyHome);

    params.getProgramParametersList().add("--main");
    params.getProgramParametersList().add(GROOVY_MAIN);

    params.getProgramParametersList().add(FileUtil.toSystemDependentName(scriptPath));
    params.getProgramParametersList().addParametersString(scriptParams);

    addScriptEncodingSettings(params, script);
  }

  private void addScriptEncodingSettings(final JavaParameters params, final VirtualFile scriptFile) {
    Charset charset = EncodingProjectManager.getInstance(getProject()).getEncoding(scriptFile, true);
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
