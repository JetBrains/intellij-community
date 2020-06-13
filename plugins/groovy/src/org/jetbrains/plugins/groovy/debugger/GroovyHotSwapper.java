// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.debugger;

import com.intellij.execution.Executor;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.JavaProgramPatcher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.PathUtil;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.io.File;
import java.util.jar.Attributes;
import java.util.regex.Pattern;

/**
 * @author peter
 */
final class GroovyHotSwapper extends JavaProgramPatcher {
  private static final Logger LOG = Logger.getInstance(GroovyHotSwapper.class);
  private static final String GROOVY_HOTSWAP_AGENT_PATH = "groovy.hotswap.agent.path";
  private static final Pattern SPRING_LOADED_PATTERN = Pattern.compile("-javaagent:.+springloaded-[^/\\\\]+\\.jar");

  private static boolean containsGroovyClasses(Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () ->
      CachedValueProvider.Result.create(
        FileTypeIndex.containsFileOfType(GroovyFileType.GROOVY_FILE_TYPE, GlobalSearchScope.projectScope(project)),
        PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static boolean hasSpringLoadedReloader(JavaParameters javaParameters) {
    for (String param : javaParameters.getVMParametersList().getParameters()) {
      if (SPRING_LOADED_PATTERN.matcher(param).matches()) {
        return true;
      }
    }

    return false;
  }

  @Override
  public void patchJavaParameters(Executor executor, RunProfile configuration, JavaParameters javaParameters) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (!executor.getId().equals(DefaultDebugExecutor.EXECUTOR_ID)) {
      return;
    }

    if (!GroovyDebuggerSettings.getInstance().ENABLE_GROOVY_HOTSWAP) {
      return;
    }

    if (hasSpringLoadedReloader(javaParameters)) {
      return;
    }

    if (!(configuration instanceof RunConfiguration)) {
      return;
    }

    final Project project = ((RunConfiguration)configuration).getProject();
    if (project == null) {
      return;
    }

    if (!LanguageLevelProjectExtension.getInstance(project).getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_5)) {
      return;
    }

    if (configuration instanceof ModuleBasedConfiguration) {
      final Module module = ((ModuleBasedConfiguration)configuration).getConfigurationModule().getModule();
      if (module != null) {
        final LanguageLevel level = EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(module);
        if (!level.isAtLeast(LanguageLevel.JDK_1_5)) {
          return;
        }
      }
    }

    Sdk jdk = javaParameters.getJdk();
    if (jdk != null) {
      String vendor = JdkUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VENDOR);
      if (vendor != null && vendor.contains("IBM")) {
        LOG.info("Due to IBM JDK peculiarities (IDEA-59070) we don't add Groovy agent when running applications under it");
        return;
      }
    }

    if (!project.isDefault() && containsGroovyClasses(project)) {
      String agentPath = JavaExecutionUtil.handleSpacesInAgentPath(getAgentJarPath(), "groovyHotSwap", GROOVY_HOTSWAP_AGENT_PATH);
      if (agentPath != null) {
        javaParameters.getVMParametersList().add("-javaagent:" + agentPath);
      }
    }
  }

  private static String getAgentJarPath() {
    final File ourJar = new File(PathUtil.getJarPathForClass(GroovyHotSwapper.class));
    if (ourJar.isDirectory()) { //development mode
      return PluginPathManager.getPluginHomePath("groovy") + "/hotswap/gragent.jar";
    }

    final File pluginDir = ourJar.getParentFile();
    return pluginDir.getPath() + File.separator + "agent" + File.separator + "gragent.jar";
  }
}