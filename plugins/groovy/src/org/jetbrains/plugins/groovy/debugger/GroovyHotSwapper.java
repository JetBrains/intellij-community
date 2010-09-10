package org.jetbrains.plugins.groovy.debugger;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.JavaProgramPatcher;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileTypeLoader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * @author peter
 */
public class GroovyHotSwapper extends JavaProgramPatcher {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.debugger.GroovyHotSwapper");
  private static final String GROOVY_HOTSWAP_AGENT_PATH = "groovy.hotswap.agent.path";

  private static boolean endsWithAny(String s, List<String> endings) {
    for (String extension : endings) {
      if (s.endsWith(extension)) {
        return true;
      }
    }
    return true;
  }

  private static boolean containsGroovyClasses(Project project) {
    final List<String> extensions = new ArrayList<String>();
    for (String extension : GroovyFileTypeLoader.getAllGroovyExtensions()) {
      extensions.add("." + extension);
    }
    final GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
    for (String fileName : FilenameIndex.getAllFilenames(project)) {
      if (endsWithAny(fileName, extensions)) {
        if (!FilenameIndex.getVirtualFilesByName(project, fileName, scope).isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  public void patchJavaParameters(Executor executor, RunProfile configuration, JavaParameters javaParameters) {
    if (!executor.getId().equals(DefaultDebugExecutor.EXECUTOR_ID)) {
      return;
    }
    if ("false".equals(System.getProperty("enable.groovy.hotswap", "true"))) {
      return;
    }

    if (!(configuration instanceof RunConfiguration)) {
      return;
    }

    final Project project = ((RunConfiguration)configuration).getProject();
    if (project == null) {
      return;
    }

    if (LanguageLevelProjectExtension.getInstance(project).getLanguageLevel().compareTo(LanguageLevel.JDK_1_5) < 0) {
      return;
    }

    if (containsGroovyClasses(project)) {
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
                 "One can move the agent to a directory with no spaces in path and specify its path in idea.properties as " + GROOVY_HOTSWAP_AGENT_PATH + "=<path>");
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
