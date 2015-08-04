/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.shell;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.config.AbstractConfigUtils;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.runner.DefaultGroovyScriptRunner;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

/**
 * @author Sergey Evdokimov
 */
public class DefaultGroovyShellRunner extends GroovyShellConfig {

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
    DefaultGroovyScriptRunner.configureGenericGroovyRunner(res, module, "org.codehaus.groovy.tools.shell.Main", false, true);
    res.setWorkingDirectory(getWorkingDirectory(module));
    return res;
  }

  @Override
  public boolean canRun(@NotNull Module module) {
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    return contentRoots.length > 0 && hasGroovyWithNeededJars(module);
  }

  @NotNull
  @Override
  public String getVersion(@NotNull Module module) {
    String homePath = LibrariesUtil.getGroovyHomePath(module);
    assert homePath != null;

    String version = GroovyConfigUtils.getInstance().getSDKVersion(homePath);
    return version == AbstractConfigUtils.UNDEFINED_VERSION ? "" : "Groovy " + version;
  }

  public static boolean hasGroovyWithNeededJars(Module module) {
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    return facade.findClass("org.apache.commons.cli.CommandLineParser", scope) != null &&
           facade.findClass("org.codehaus.groovy.tools.shell.Main", scope) != null &&
           facade.findClass("org.fusesource.jansi.AnsiConsole", scope) != null;
  }

  @Override
  public boolean isSuitableModule(Module module) {
    return super.isSuitableModule(module) && hasGroovyWithNeededJars(module);
  }

  @Override
  public String getTitle() {
    return "Groovy Shell";
  }
}
