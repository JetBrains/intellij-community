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
package org.jetbrains.plugins.groovy.debugger;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.JavaProgramPatcher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.io.File;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public class GroovyHotSwapper extends JavaProgramPatcher {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.debugger.GroovyHotSwapper");
  private static final String GROOVY_HOTSWAP_AGENT_PATH = "groovy.hotswap.agent.path";
  private static final Pattern SPRING_LOADED_PATTERN = Pattern.compile("-javaagent:.+springloaded-[^/\\\\]+\\.jar");
  
  private static boolean containsGroovyClasses(Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () ->
      CachedValueProvider.Result.create(
        FileTypeIndex.containsFileOfType(GroovyFileType.GROOVY_FILE_TYPE, GlobalSearchScope.projectScope(project)),
        PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT));
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
        final LanguageLevel level = LanguageLevelModuleExtensionImpl.getInstance(module).getLanguageLevel();
        if (level != null && !level.isAtLeast(LanguageLevel.JDK_1_5)) {
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
      final String agentPath = handleSpacesInPath(getAgentJarPath());
      if (agentPath != null) {
        javaParameters.getVMParametersList().add("-javaagent:" + agentPath);
      }
    }
  }

  @Nullable
  private static String handleSpacesInPath(String agentPath) {
    if (agentPath.contains(" ")) {
      final File dir = new File(PathManager.getSystemPath(), "groovyHotSwap");
      if (dir.getAbsolutePath().contains(" ")) {
        LOG.info("Groovy hot-swap not used since the agent path contains spaces: " + agentPath + "\n" +
                 "One can move the agent to a directory with no spaces in path," +
                 " and specify its path in <IDEA dist>/bin/idea.properties as " + GROOVY_HOTSWAP_AGENT_PATH + "=<path>");
        return null;
      }

      final File toFile = new File(dir, "gragent.jar");
      try {
        FileUtil.copy(new File(agentPath), toFile);
        return toFile.getPath();
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    return agentPath;
  }

  private static String getAgentJarPath() {
    final String userDefined = System.getProperty(GROOVY_HOTSWAP_AGENT_PATH);
    if (userDefined != null && new File(userDefined).exists()) {
      return userDefined;
    }

    final File ourJar = new File(PathUtil.getJarPathForClass(GroovyHotSwapper.class));
    if (ourJar.isDirectory()) { //development mode
      return PluginPathManager.getPluginHomePath("groovy") + "/hotswap/gragent.jar";
    }

    final File pluginDir = ourJar.getParentFile();
    return pluginDir.getPath() + File.separator + "agent" + File.separator + "gragent.jar";
  }
}