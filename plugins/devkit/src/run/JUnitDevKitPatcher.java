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
package org.jetbrains.idea.devkit.run;

import com.intellij.execution.JUnitPatcher;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.Computable;
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

/**
 * @author anna
 * @since Mar 4, 2005
 */
public class JUnitDevKitPatcher extends JUnitPatcher {
  private static final String SYSTEM_CL_PROPERTY = "java.system.class.loader";

  @Override
  public void patchJavaParameters(@Nullable final Module module, JavaParameters javaParameters) {
    if (module != null && PsiUtil.isIdeaProject(module.getProject()) && !javaParameters.getVMParametersList().hasProperty(SYSTEM_CL_PROPERTY)) {
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(module.getProject());
      final String qualifiedName = UrlClassLoader.class.getName();
      final PsiClass urlLoaderClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
        @Override
        public PsiClass compute() {
          return psiFacade.findClass(qualifiedName, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
        }
      });
      if (urlLoaderClass != null) {
        javaParameters.getVMParametersList().addProperty(SYSTEM_CL_PROPERTY, qualifiedName);
      }
    }

    Sdk jdk = javaParameters.getJdk();
    jdk = IdeaJdk.findIdeaJdk(jdk);
    if (jdk == null) return;

    String libPath = jdk.getHomePath() + File.separator + "lib";

    final ParametersList vm = javaParameters.getVMParametersList();
    vm.add("-Xbootclasspath/a:" + libPath + File.separator + "boot.jar");
    if (!vm.hasProperty("idea.load.plugins.id") && module != null && PluginModuleType.isOfType(module)) {
      final String id = DescriptorUtil.getPluginId(module);
      if (id != null) {
        vm.defineProperty("idea.load.plugins.id", id);
      }
    }

    final File sandboxHome = getSandboxPath(jdk);
    if (sandboxHome != null) {
      if (!vm.hasProperty("idea.home.path")) {
        File homeDir = new File(sandboxHome, "test");
        FileUtil.createDirectory(homeDir);
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