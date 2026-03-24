// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run;

import com.intellij.execution.JUnitPatcher;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.platform.eel.provider.utils.EelPathUtils;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.projectRoots.Sandbox;
import org.jetbrains.idea.devkit.requestHandlers.BuiltInServerConnectionData;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;

import static com.intellij.platform.ijent.community.buildConstants.IjentBuildScriptsConstantsKt.IJENT_BOOT_CLASSPATH_MODULE;

@ApiStatus.Internal
public final class JUnitDevKitPatcher extends JUnitPatcher {
  private static final Logger LOG = Logger.getInstance(JUnitDevKitPatcher.class);

  @Override
  public void patchJavaParameters(@NotNull Project project, @Nullable Module module, JavaParameters javaParameters) {
    var jdk = javaParameters.getJdk();
    if (jdk == null) return;

    var vm = javaParameters.getVMParametersList();

    if (IntelliJProjectUtil.isIntelliJPlatformProject(project)) {
      BuiltInServerConnectionData.passDataAboutBuiltInServer(javaParameters, project);
      
      if (!vm.hasProperty(DevKitPatcherHelper.SYSTEM_CL_PROPERTY) && !vm.getList().contains("--add-modules")) {
        // check that UrlClassLoader is available in the test module classpath
        // if module-path is used, skip custom loader
        var qualifiedName = "com.intellij.util.lang.UrlClassLoader";
        if (DevKitPatcherHelper.loaderValid(project, module, qualifiedName)) {
          vm.addProperty(DevKitPatcherHelper.SYSTEM_CL_PROPERTY, qualifiedName);
          vm.addProperty(UrlClassLoader.CLASSPATH_INDEX_PROPERTY_NAME, "true");
        }
      }

      var basePath = project.getBasePath();
      if (module != null && hasIjentDefaultFsProviderInClassPath(module)) {
        DevKitPatcherHelper.enableIjentDefaultFsProvider(project, vm);
      }
      if (!vm.hasProperty(PathManager.PROPERTY_SYSTEM_PATH)) {
        assert basePath != null;
        vm.addProperty(PathManager.PROPERTY_SYSTEM_PATH, EelPathUtils.renderAsEelPath(Path.of(basePath, "system/test").toAbsolutePath()));
      }
      if (!vm.hasProperty(PathManager.PROPERTY_CONFIG_PATH)) {
        assert basePath != null;
        vm.addProperty(PathManager.PROPERTY_CONFIG_PATH, EelPathUtils.renderAsEelPath(Path.of(basePath, "config/test").toAbsolutePath()));
      }

      DevKitPatcherHelper.appendAddOpensWhenNeeded(project, jdk, vm);

      if (!Boolean.parseBoolean(vm.getPropertyValue("intellij.devkit.junit.skip.settings.from.intellij.yaml"))) {
        JUnitDevKitUnitTestingSettings.getInstance(project).apply(module, javaParameters);
      }
    }

    jdk = IdeaJdk.findIdeaJdk(jdk);
    if (jdk == null) return;

    if (!vm.hasProperty("idea.load.plugins.id") && module != null && PluginModuleType.isOfType(module)) {
      //non-optional dependencies of 'idea.load.plugin.id' are automatically enabled (see com.intellij.ide.plugins.PluginManagerCore.detectReasonToNotLoad)
      //we need to explicitly add optional dependencies to properly test them
      var ids = DescriptorUtil.getPluginAndOptionalDependenciesIds(module);
      if (!ids.isEmpty()) {
        vm.defineProperty("idea.load.plugins.id", String.join(",", ids));
      }
    }

    var sandboxHome = getSandboxPath(jdk);
    if (sandboxHome != null) {
      if (!vm.hasProperty(PathManager.PROPERTY_HOME_PATH)) {
        var homeDir = sandboxHome.resolve("test");
        try {
          Files.createDirectories(homeDir);
        }
        catch (IOException e) {
          LOG.error(e);
        }

        var buildNumber = IdeaJdk.getBuildNumber(jdk.getHomePath());
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

    @SuppressWarnings({"UnnecessaryFullyQualifiedName", "IO_FILE_USAGE"})
    var libPath = jdk.getHomePath() + java.io.File.separator + "lib" + java.io.File.separator;
    javaParameters.getClassPath().addFirst(libPath + "idea.jar");
    javaParameters.getClassPath().addFirst(libPath + "resources.jar");
  }


  private static @Nullable Path getSandboxPath(Sdk jdk) {
    if (jdk.getSdkAdditionalData() instanceof Sandbox sandbox) {
      var sandboxHome = sandbox.getSandboxHome();
      if (sandboxHome != null) {
        return Path.of(sandboxHome).normalize().toAbsolutePath();
      }
    }
    return null;
  }

  private static boolean hasIjentDefaultFsProviderInClassPath(Module startModule) {
    var queue = new ArrayDeque<>(List.of(ModuleRootManager.getInstance(startModule).getModuleDependencies()));
    var seen = new HashSet<Module>();
    seen.add(startModule);
    while (!queue.isEmpty()) {
      var module = queue.removeFirst();
      if (IJENT_BOOT_CLASSPATH_MODULE.equals(module.getName())) {
        return true;
      }
      if (seen.add(module)) {
        queue.addAll(List.of(ModuleRootManager.getInstance(module).getModuleDependencies()));
      }
    }
    return false;
  }
}
