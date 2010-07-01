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
package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

import java.io.File;
import java.io.IOException;

/**
 * @author peter
 */
public abstract class GroovyScriptRunner {

  public abstract boolean isValidModule(@NotNull Module module);

  public abstract boolean ensureRunnerConfigured(@Nullable Module module, RunProfile profile, Executor executor, final Project project) throws ExecutionException;

  public abstract void configureCommandLine(JavaParameters params, @Nullable Module module, boolean tests, VirtualFile script,
                                            GroovyScriptRunConfiguration configuration) throws CantRunException;

  protected static String getConfPath(final String groovyHomePath) {
    String confpath = FileUtil.toSystemDependentName(groovyHomePath + "/conf/groovy-starter.conf");
    if (new File(confpath).exists()) {
      return confpath;
    }

    try {
      final String jarPath = PathUtil.getJarPathForClass(GroovyScriptRunner.class);
      if (new File(jarPath).isFile()) { //jar; distribution mode
        return new File(jarPath, "../groovy-starter.conf").getCanonicalPath();
      }

      //else, it's directory in out, development mode
      return new File(jarPath, "conf/groovy-starter.conf").getCanonicalPath();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static void setGroovyHome(JavaParameters params, String groovyHome) {
    params.getVMParametersList().add("-Dgroovy.home=" + groovyHome);
    if (groovyHome.contains("grails")) { //a bit of a hack
      params.getVMParametersList().add("-Dgrails.home=" + groovyHome);
    }
    if (groovyHome.contains("griffon")) { //a bit of a hack
      params.getVMParametersList().add("-Dgriffon.home=" + groovyHome);
    }
  }

  protected static void setToolsJar(JavaParameters params) {
    Sdk jdk = params.getJdk();
    if (jdk != null && jdk.getSdkType() instanceof JavaSdkType) {
      String toolsPath = ((JavaSdkType)jdk.getSdkType()).getToolsPath(jdk);
      if (toolsPath != null) {
        params.getVMParametersList().add("-Dtools.jar=" + toolsPath);
      }
    }
  }

  @Nullable
  protected static VirtualFile findGroovyJar(@NotNull Module module) {
    final VirtualFile[] files = ModuleRootManager.getInstance(module).getFiles(OrderRootType.CLASSES);
    for (VirtualFile root : files) {
      if (root.getName().matches(GroovyConfigUtils.GROOVY_JAR_PATTERN) || root.getName().matches(GroovyConfigUtils.GROOVY_ALL_JAR_PATTERN)) {
        return root;
      }
    }
    for (VirtualFile file : files) {
      if (file.getName().contains("groovy") && "jar".equals(file.getExtension())) {
        return file;
      }
    }
    return null;
  }

  protected static void addClasspathFromRootModel(@Nullable Module module, boolean isTests, JavaParameters params) throws CantRunException {
    if (module == null) {
      return;
    }

    final JavaParameters tmp = new JavaParameters();
    tmp.configureByModule(module, isTests ? JavaParameters.CLASSES_AND_TESTS : JavaParameters.CLASSES_ONLY);
    if (tmp.getClassPath().getVirtualFiles().isEmpty()) {
      return;
    }

    params.getProgramParametersList().add("--classpath");
    params.getProgramParametersList().add(tmp.getClassPath().getPathsString());
  }

}
