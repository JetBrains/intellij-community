/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.execution.JUnitPatcher;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.projectRoots.Sandbox;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author anna
 * @since Mar 4, 2005
 */
public class JUnitDevKitPatcher extends JUnitPatcher {
  private static final Logger LOG = Logger.getInstance(JUnitDevKitPatcher.class);
  private static final String SYSTEM_CL_PROPERTY = "java.system.class.loader";

  @Override
  public void patchJavaParameters(@Nullable Module module, JavaParameters javaParameters) {
    Sdk jdk = javaParameters.getJdk();
    if (jdk == null) return;

    ParametersList vm = javaParameters.getVMParametersList();

    if (module != null &&
        PsiUtil.isIdeaProject(module.getProject()) &&
        !vm.hasProperty(SYSTEM_CL_PROPERTY) &&
        !JavaSdk.getInstance().isOfVersionOrHigher(jdk, JavaSdkVersion.JDK_1_9)) {
      String qualifiedName = UrlClassLoader.class.getName();
      if (findLoader(module, qualifiedName) != null) {
        vm.addProperty(SYSTEM_CL_PROPERTY, qualifiedName);
      }
    }

    jdk = IdeaJdk.findIdeaJdk(jdk);
    if (jdk == null) return;

    String libPath = jdk.getHomePath() + File.separator + "lib";
    String bootJarPath = libPath + File.separator + "boot.jar";
    if (new File(bootJarPath).exists()) {
      //there is no need to add boot.jar in modern IDE builds (181.*)
      vm.add("-Xbootclasspath/a:" + bootJarPath);
    }

    if (!vm.hasProperty("idea.load.plugins.id") && module != null && PluginModuleType.isOfType(module)) {
      String id = DescriptorUtil.getPluginId(module);
      if (id != null) {
        vm.defineProperty("idea.load.plugins.id", id);
      }
    }

    File sandboxHome = getSandboxPath(jdk);
    if (sandboxHome != null) {
      if (!vm.hasProperty("idea.home.path")) {
        File homeDir = new File(sandboxHome, "test");
        FileUtil.createDirectory(homeDir);
        String buildNumber = IdeaJdk.getBuildNumber(jdk.getHomePath());
        if (buildNumber != null) {
          try {
            FileUtil.writeToFile(new File(homeDir, "build.txt"), buildNumber);
          }
          catch (IOException e) {
            LOG.warn("failed to create build.txt in " + homeDir + ": " + e.getMessage(), e);
          }
        }
        else {
          LOG.warn("Cannot determine build number for " + jdk.getHomePath());
        }
        vm.defineProperty("idea.home.path", homeDir.getAbsolutePath());
      }
      if (!vm.hasProperty("idea.plugins.path")) {
        vm.defineProperty("idea.plugins.path", new File(sandboxHome, "plugins").getAbsolutePath());
      }
    }

    javaParameters.getClassPath().addFirst(libPath + File.separator + "idea.jar");
    javaParameters.getClassPath().addFirst(libPath + File.separator + "resources.jar");
    javaParameters.getClassPath().addFirst(((JavaSdkType)jdk.getSdkType()).getToolsPath(jdk));
  }

  private static PsiClass findLoader(Module module, String qualifiedName) {
    Project project = module.getProject();
    DumbService dumbService = DumbService.getInstance(project);
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    dumbService.setAlternativeResolveEnabled(true);
    try {
      return ReadAction.compute(() -> facade.findClass(qualifiedName, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)));
    }
    finally {
      dumbService.setAlternativeResolveEnabled(false);
    }
  }

  @Nullable
  private static File getSandboxPath(final Sdk jdk) {
    SdkAdditionalData additionalData = jdk.getSdkAdditionalData();
    if (additionalData instanceof Sandbox) {
      String sandboxHome = ((Sandbox)additionalData).getSandboxHome();
      if (sandboxHome != null) {
        return new File(FileUtil.toCanonicalPath(sandboxHome));
      }
    }

    return null;
  }
}