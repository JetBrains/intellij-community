/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.console;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.config.AbstractConfigUtils;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.runner.DefaultGroovyScriptRunner;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunner;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

/**
 * @author Max Medvedev
 */
public class GroovyConsoleRunner extends GroovyShellRunner {
  @NotNull
  @Override
  public String getWorkingDirectory(@NotNull Module module) {
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    return contentRoots[0].getPath();
  }

  @NotNull
  @Override
  public JavaParameters createJavaParameters(@NotNull Module module) throws ExecutionException {
    JavaParameters res = GroovyScriptRunConfiguration.createJavaParametersWithSdk(module);
    DefaultGroovyScriptRunner.configureGenericGroovyRunner(res, module, "groovy.ui.GroovyMain", !hasGroovyAll(module), true);
    PathsList list = GroovyScriptRunner.getClassPathFromRootModel(module, true, res, true, res.getClassPath());
    if (list != null) {
      res.getClassPath().addAll(list.getPathList());
    }
    res.getProgramParametersList().addAll("-p", GroovyScriptRunner.getPathInConf("console.txt"));
    //res.getVMParametersList().add("-Xdebug"); res.getVMParametersList().add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5239");
    res.setWorkingDirectory(getWorkingDirectory(module));

    return res;
  }

  @Override
  public boolean canRun(@NotNull Module module) {
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    return contentRoots.length > 0;
  }

  @NotNull
  @Override
  public String getTitle(@NotNull Module module) {
    String homePath = LibrariesUtil.getGroovyHomePath(module);
    boolean bundled = false;
    if (homePath == null || !hasGroovyAll(module)) {
      homePath = GroovyUtils.getBundledGroovyJar().getParentFile().getParent();
      bundled = true;
    }
    String version = GroovyConfigUtils.getInstance().getSDKVersion(homePath);
    return version == AbstractConfigUtils.UNDEFINED_VERSION ? "" : " (" + (bundled ? "Bundled " : "") + "Groovy " + version + ")";
  }

  private static boolean hasGroovyAll(Module module) {
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    return (facade.findClass("org.apache.commons.cli.CommandLineParser", scope) != null ||
            facade.findClass("groovyjarjarcommonscli.CommandLineParser", scope) != null) &&
           facade.findClass("groovy.ui.GroovyMain", scope) != null;
  }

  @NotNull
  @Override
  public String transformUserInput(@NotNull String userInput) {
    return StringUtil.replace(userInput, "\n", "###\\n");
  }
}
