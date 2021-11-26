// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.run;

import com.intellij.execution.JUnitPatcher;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.projectRoots.Sandbox;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class JUnitDevKitPatcher extends JUnitPatcher {
  private static final Logger LOG = Logger.getInstance(JUnitDevKitPatcher.class);
  private static final String SYSTEM_CL_PROPERTY = "java.system.class.loader";

  @Override
  public void patchJavaParameters(@NotNull Project project, @Nullable Module module, JavaParameters javaParameters) {
    Sdk jdk = javaParameters.getJdk();
    if (jdk == null) {
      return;
    }

    ParametersList vm = javaParameters.getVMParametersList();

    if (PsiUtil.isIdeaProject(project)) {
      if (!vm.hasProperty(SYSTEM_CL_PROPERTY)) {
        vm.addProperty(SYSTEM_CL_PROPERTY, "com.intellij.util.lang.UrlClassLoader");
      }
      String basePath = project.getBasePath();
      if (!vm.hasProperty(PathManager.PROPERTY_SYSTEM_PATH)) {
        assert basePath != null;
        vm.addProperty(PathManager.PROPERTY_SYSTEM_PATH, Path.of(basePath, "system/test").toAbsolutePath().toString());
      }
      if (!vm.hasProperty(PathManager.PROPERTY_CONFIG_PATH)) {
        assert basePath != null;
        vm.addProperty(PathManager.PROPERTY_CONFIG_PATH, Path.of(basePath, "config/test").toAbsolutePath().toString());
      }
    }

    jdk = IdeaJdk.findIdeaJdk(jdk);
    if (jdk == null) {
      return;
    }

    @NonNls String libPath = jdk.getHomePath() + File.separator + "lib";
    @NonNls String bootJarPath = libPath + File.separator + "boot.jar";
    if (Files.exists(Path.of(bootJarPath))) {
      // there is no need to add boot.jar in modern IDE builds (181.*)
      vm.add("-Xbootclasspath/a:" + bootJarPath);
    }

    if (!vm.hasProperty("idea.load.plugins.id") && module != null && PluginModuleType.isOfType(module)) {
      //non-optional dependencies of 'idea.load.plugin.id' are automatically enabled (see com.intellij.ide.plugins.PluginManagerCore.detectReasonToNotLoad)
      //we need to explicitly add optional dependencies to properly test them
      List<String> ids = DescriptorUtil.getPluginAndOptionalDependenciesIds(module);
      if (!ids.isEmpty()) {
        vm.defineProperty("idea.load.plugins.id", String.join(",", ids));
      }
    }

    Path sandboxHome = getSandboxPath(jdk);
    if (sandboxHome != null) {
      if (!vm.hasProperty(PathManager.PROPERTY_HOME_PATH)) {
        Path homeDir = sandboxHome.resolve("test");
        try {
          Files.createDirectories(homeDir);
        }
        catch (IOException e) {
          LOG.error(e);
        }

        String buildNumber = IdeaJdk.getBuildNumber(jdk.getHomePath());
        if (buildNumber != null) {
          try {
            Files.writeString(homeDir.resolve("build.txt"), buildNumber);
          }
          catch (IOException e) {
            LOG.warn("failed to create build.txt in " + homeDir + ": " + e.getMessage(), e);
          }
        }
        else {
          LOG.warn("Cannot determine build number for " + jdk.getHomePath());
        }
        vm.defineProperty(PathManager.PROPERTY_HOME_PATH, homeDir.toString());
      }
      if (!vm.hasProperty(PathManager.PROPERTY_PLUGINS_PATH)) {
        vm.defineProperty(PathManager.PROPERTY_PLUGINS_PATH, sandboxHome.resolve("plugins").toString());
      }
    }

    javaParameters.getClassPath().addFirst(libPath + File.separator + "idea.jar");
    javaParameters.getClassPath().addFirst(libPath + File.separator + "resources.jar");
    javaParameters.getClassPath().addFirst(((JavaSdkType)jdk.getSdkType()).getToolsPath(jdk));
  }

  private static @Nullable Path getSandboxPath(Sdk jdk) {
    SdkAdditionalData additionalData = jdk.getSdkAdditionalData();
    if (additionalData instanceof Sandbox) {
      String sandboxHome = ((Sandbox)additionalData).getSandboxHome();
      if (sandboxHome != null) {
        return Path.of(sandboxHome).normalize().toAbsolutePath();
      }
    }
    return null;
  }
}