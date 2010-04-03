package org.jetbrains.plugins.groovy.debugger;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.JavaProgramPatcher;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.util.PathUtil;
import org.jetbrains.plugins.groovy.GroovyFileTypeLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * @author peter
 */
public class GroovyHotSwapper extends JavaProgramPatcher {

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
    for (String fileName : FilenameIndex.getAllFilenames(project)) {
      if (endsWithAny(fileName, extensions)) {
        return true;
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
      javaParameters.getVMParametersList().add("-javaagent:" + getAgentJarPath());
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
