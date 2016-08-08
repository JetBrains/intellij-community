/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public abstract class GroovyScriptRunner {

  public abstract boolean isValidModule(@NotNull Module module);

  public abstract boolean ensureRunnerConfigured(@Nullable Module module, RunProfile profile, Executor executor, final Project project) throws ExecutionException;

  public abstract void configureCommandLine(JavaParameters params, @Nullable Module module, boolean tests, VirtualFile script,
                                            GroovyScriptRunConfiguration configuration) throws CantRunException;

  public boolean shouldRefreshAfterFinish() {
    return false;
  }

  protected static String getConfPath(final String groovyHomePath) {
    String confpath = FileUtil.toSystemDependentName(groovyHomePath + "/conf/groovy-starter.conf");
    if (new File(confpath).exists()) {
      return confpath;
    }

    return getPathInConf("groovy-starter.conf");
  }

  public static String getPathInConf(String fileName) {
    try {
      final String jarPath = PathUtil.getJarPathForClass(GroovyLanguage.class);
      if (new File(jarPath).isFile()) { //jar; distribution mode
        return new File(jarPath, "../" + fileName).getCanonicalPath();
      }

      //else, it's directory in out, development mode
      return new File(jarPath, "conf/" + fileName).getCanonicalPath();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void setGroovyHome(JavaParameters params, @NotNull String groovyHome) {
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
    final VirtualFile[] files = OrderEnumerator.orderEntries(module).getAllLibrariesAndSdkClassesRoots();
    for (VirtualFile root : files) {
      if (GroovyConfigUtils.GROOVY_JAR_PATTERN.matcher(root.getName()).matches() || GroovyConfigUtils.matchesGroovyAll(root.getName())) {
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

  protected static void addClasspathFromRootModel(@Nullable Module module, boolean isTests, JavaParameters params, boolean allowDuplication) throws CantRunException {
    PathsList nonCore = new PathsList();
    getClassPathFromRootModel(module, isTests, params, allowDuplication, nonCore);

    final String cp = nonCore.getPathsString();
    if (!StringUtil.isEmptyOrSpaces(cp)) {
      params.getProgramParametersList().add("--classpath");
      params.getProgramParametersList().add(cp);
    }
  }

  @Nullable
  public static PathsList getClassPathFromRootModel(Module module,
                                                    boolean isTests,
                                                    JavaParameters params,
                                                    boolean allowDuplication,
                                                    PathsList pathList)
    throws CantRunException {
    if (module == null) {
      return null;
    }

    pathList.add(".");

    final JavaParameters tmp = new JavaParameters();
    tmp.configureByModule(module, isTests ? JavaParameters.CLASSES_AND_TESTS : JavaParameters.CLASSES_ONLY);
    if (tmp.getClassPath().getVirtualFiles().isEmpty()) {
      return null;
    }

    Set<VirtualFile> core = new HashSet<>(params.getClassPath().getVirtualFiles());

    for (VirtualFile virtualFile : tmp.getClassPath().getVirtualFiles()) {
      if (allowDuplication || !core.contains(virtualFile)) {
        pathList.add(virtualFile);
      }
    }
    return pathList;
  }
}
