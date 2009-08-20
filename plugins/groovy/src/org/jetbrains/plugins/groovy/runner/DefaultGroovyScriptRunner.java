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
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.nio.charset.Charset;

public class DefaultGroovyScriptRunner extends GroovyScriptRunner {

  @Override
  public boolean isValidModule(Module module) {
    return LibrariesUtil.hasGroovySdk(module);
  }

  @Override
  public void configureCommandLine(JavaParameters params, Module module, boolean tests, VirtualFile script, String confPath,
                                      final String groovyHome, GroovyScriptRunConfiguration configuration) throws CantRunException {
    defaultGroovyStarter(params, module, confPath, tests, groovyHome, configuration);

    params.getProgramParametersList().add("--main");
    params.getProgramParametersList().add("groovy.ui.GroovyMain");

    params.getProgramParametersList().add(FileUtil.toSystemDependentName(configuration.scriptPath));
    params.getProgramParametersList().addParametersString(configuration.scriptParams);

    addScriptEncodingSettings(params, script, module);
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
