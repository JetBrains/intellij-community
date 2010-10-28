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
package com.intellij.lang.ant.config.execution;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.rt.compiler.JavacRunner;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ComparatorUtil;
import static com.intellij.util.containers.ContainerUtil.map;
import static com.intellij.util.containers.ContainerUtil.skipNulls;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PathUtilEx {
  @NonNls private static final String IDEA_PREPEND_RTJAR = "idea.prepend.rtjar";

  private static final Function<Module, Sdk> MODULE_JDK = new Function<Module, Sdk>() {
    @Nullable
    public Sdk fun(Module module) {
      return ModuleRootManager.getInstance(module).getSdk();
    }
  };
  private static final Convertor<Sdk, String> SDK_VERSION = new Convertor<Sdk, String>() {
    public String convert(Sdk sdk) {
      return sdk.getVersionString();
    }
  };

  private PathUtilEx() {
  }

  public static void addRtJar(PathsList pathsList) {
    final String ideaRtJarPath = getIdeaRtJarPath();
    if (Boolean.getBoolean(IDEA_PREPEND_RTJAR)) {
      pathsList.addFirst(ideaRtJarPath);
    }
    else {
      pathsList.addTail(ideaRtJarPath);
    }
  }

  public static String getIdeaRtJarPath() {
    final Class aClass = JavacRunner.class;
    return PathUtil.getJarPathForClass(aClass);
  }

  public static Sdk getAnyJdk(Project project) {
    return chooseJdk(project, Arrays.asList(ModuleManager.getInstance(project).getModules()));
  }

  public static Sdk chooseJdk(Project project, Collection<Module> modules) {
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (projectSdk != null) {
      return projectSdk;
    }
    return chooseJdk(modules);
  }

  public static Sdk chooseJdk(Collection<Module> modules) {
    List<Sdk> sdks = skipNulls(map(skipNulls(modules), MODULE_JDK));
    if (sdks.isEmpty()) {
      return null;
    }
    Collections.sort(sdks, ComparatorUtil.compareBy(SDK_VERSION, String.CASE_INSENSITIVE_ORDER));
    return sdks.get(sdks.size() - 1);
  }
}

