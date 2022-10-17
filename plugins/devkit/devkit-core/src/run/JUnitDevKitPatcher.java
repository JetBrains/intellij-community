// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run;

import com.intellij.execution.JUnitPatcher;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.JavaModuleOptions;
import com.intellij.util.system.OS;
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
  static final String SYSTEM_CL_PROPERTY = "java.system.class.loader";
  private static final Key<Boolean> LOADER_VALID = Key.create("LOADER_VALID_9");

  @Override
  public void patchJavaParameters(@NotNull Project project, @Nullable Module module, JavaParameters javaParameters) {
    Sdk jdk = javaParameters.getJdk();
    if (jdk == null) {
      return;
    }

    ParametersList vm = javaParameters.getVMParametersList();

    if (PsiUtil.isIdeaProject(project)) {
      if (!vm.hasProperty(SYSTEM_CL_PROPERTY) && !vm.getList().contains("--add-modules")) {
        // check that UrlClassLoader is available in the test module classpath
        // if module-path is used, skip custom loader
        String qualifiedName = "com.intellij.util.lang.UrlClassLoader";
        if (loaderValid(project, module, qualifiedName)) {
          vm.addProperty(SYSTEM_CL_PROPERTY, qualifiedName);
        }
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

      appendAddOpensWhenNeeded(project, jdk, vm);
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

  static void appendAddOpensWhenNeeded(@NotNull Project project, @NotNull Sdk jdk, @NotNull ParametersList vm) {
    var sdkVersion = ((JavaSdk)jdk.getSdkType()).getVersion(jdk);
    if (sdkVersion != null && sdkVersion.isAtLeast(JavaSdkVersion.JDK_17)) {
      var scope = ProjectScope.getContentScope(project);
      var files = ReadAction.compute(() -> FilenameIndex.getVirtualFilesByName("OpenedPackages.txt", scope));
      if (files.size() > 1) {
        LOG.error("expecting 1 file, found: " + files);
      }
      else if (!files.isEmpty()) {
        var file = files.iterator().next();
        try (var stream = file.getInputStream()) {
          JavaModuleOptions.readOptions(stream, OS.CURRENT).forEach(vm::add);
        }
        catch (ProcessCanceledException e) {
          throw e; //unreachable
        }
        catch (Throwable e) {
          LOG.error("Failed to load --add-opens list from 'OpenedPackages.txt'", e);
        }
      }
    }
  }

  static boolean loaderValid(Project project, Module module, String qualifiedName) {
    UserDataHolder holder = module == null ? project : module;
    Key<Boolean> cacheKey = LOADER_VALID;
    Boolean result = holder.getUserData(cacheKey);
    if (result == null) {
      result = ReadAction.compute(() -> {
        //noinspection RedundantCast
        return DumbService.getInstance(project).computeWithAlternativeResolveEnabled((ThrowableComputable<Boolean, RuntimeException>)() -> {
          GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleRuntimeScope(module, true)
                                                   : GlobalSearchScope.allScope(project);
          return JavaPsiFacade.getInstance(project).findClass(qualifiedName, scope) != null;
        });
      });
      holder.putUserData(cacheKey, result);
    }
    return result;
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
