// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.projectRoots.Sandbox;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author anna
 */
public class JUnitDevKitPatcher extends JUnitPatcher {
  private static final Logger LOG = Logger.getInstance(JUnitDevKitPatcher.class);
  private static final String SYSTEM_CL_PROPERTY = "java.system.class.loader";

  @Override
  public void patchJavaParameters(@NotNull Project project, @Nullable Module module, JavaParameters javaParameters) {
    Sdk jdk = javaParameters.getJdk();
    if (jdk == null) return;

    ParametersList vm = javaParameters.getVMParametersList();

    if (PsiUtil.isIdeaProject(project) && !vm.hasProperty(SYSTEM_CL_PROPERTY)) {
      String qualifiedName = UrlClassLoader.class.getName();
      if (loaderValid(project, module, qualifiedName, jdk)) {
        vm.addProperty(SYSTEM_CL_PROPERTY, qualifiedName);
      }
    }
    
    if (Registry.is("idea.lazy.classloading.caches") &&
        vm.hasProperty(SYSTEM_CL_PROPERTY) && 
        UrlClassLoader.class.getName().equals(vm.getPropertyValue(SYSTEM_CL_PROPERTY))) {
      vm.addProperty("idea.lazy.classloading.caches", "true");
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
      //non-optional dependencies of 'idea.load.plugin.id' are automatically enabled (see com.intellij.ide.plugins.PluginManagerCore.detectReasonToNotLoad)
      //we need to explicitly add optional dependencies to properly test them
      List<String> ids = DescriptorUtil.getPluginAndOptionalDependenciesIds(module);
      if (!ids.isEmpty()) {
        vm.defineProperty("idea.load.plugins.id", StringUtil.join(ids, ","));
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

  private static final Key<Boolean> LOADER_VALID_8 = Key.create("LOADER_VALID_8");
  private static final Key<Boolean> LOADER_VALID_9 = Key.create("LOADER_VALID_9");

  private static boolean loaderValid(Project project, Module module, String qualifiedName, Sdk jdk) {
    boolean jdk9 = JavaSdk.getInstance().isOfVersionOrHigher(jdk, JavaSdkVersion.JDK_1_9);
    if (jdk9 && !Registry.is("idea.use.loader.for.jdk9")) {
      return false;
    }
    UserDataHolder holder = module != null ? module : project;
    Key<Boolean> cacheKey = jdk9 ? LOADER_VALID_9 : LOADER_VALID_8;
    Boolean res = holder.getUserData(cacheKey);
    if (res == null) {
      res = ReadAction.compute(() -> {
        //noinspection RedundantCast
        return DumbService.getInstance(project).computeWithAlternativeResolveEnabled((ThrowableComputable<Boolean, RuntimeException>)() -> {
          PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, module != null ? GlobalSearchScope
            .moduleWithDependenciesAndLibrariesScope(module) : GlobalSearchScope.allScope(project));
          if (aClass != null) {
            if (jdk9) {
              PsiClass builder = aClass.findInnerClassByName(UrlClassLoader.Builder.class.getSimpleName(), false);
              return builder != null && !ArrayUtil.isEmpty(builder.findMethodsByName("urlsFromAppClassLoader"));
            }
            else {
              return true;
            }
          }
          return false;
        });
      });
      holder.putUserData(cacheKey, res);
    }
    return res;
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