// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run;

import com.intellij.execution.JUnitPatcher;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.JavaModuleOptions;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.projectRoots.Sandbox;
import org.jetbrains.idea.devkit.requestHandlers.BuiltInServerConnectionData;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

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

    if (IntelliJProjectUtil.isIntelliJPlatformProject(project)) {
      BuiltInServerConnectionData.passDataAboutBuiltInServer(javaParameters, project);
      
      if (!vm.hasProperty(SYSTEM_CL_PROPERTY) && !vm.getList().contains("--add-modules")) {
        // check that UrlClassLoader is available in the test module classpath
        // if module-path is used, skip custom loader
        String qualifiedName = "com.intellij.util.lang.UrlClassLoader";
        if (loaderValid(project, module, qualifiedName)) {
          vm.addProperty(SYSTEM_CL_PROPERTY, qualifiedName);
          vm.addProperty(UrlClassLoader.CLASSPATH_INDEX_PROPERTY_NAME, "true");
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

    var libPath = jdk.getHomePath() + File.separator + "lib" + File.separator;
    javaParameters.getClassPath().addFirst(libPath + "idea.jar");
    javaParameters.getClassPath().addFirst(libPath + "resources.jar");
  }

  static void appendAddOpensWhenNeeded(@NotNull Project project, @NotNull Sdk jdk, @NotNull ParametersList vm) {
    var sdkVersion = jdk.getSdkType() instanceof JavaSdk javaSdk ? javaSdk.getVersion(jdk) : null;
    if (sdkVersion != null && sdkVersion.isAtLeast(JavaSdkVersion.JDK_17)) {
      var scope = ProjectScope.getContentScope(project);
      var files = ReadAction.compute(() -> FilenameIndex.getVirtualFilesByName("OpenedPackages.txt", scope));
      if (files.size() > 1) {
        var list = files.stream().map(VirtualFile::getPresentableUrl).collect(Collectors.joining("\n"));
        var message = DevKitBundle.message("notification.message.duplicate.packages.file", list);
        new Notification("DevKit Errors", message, NotificationType.ERROR).notify(project);
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
