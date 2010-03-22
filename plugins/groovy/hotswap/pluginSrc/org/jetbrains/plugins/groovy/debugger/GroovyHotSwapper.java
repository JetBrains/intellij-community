package org.jetbrains.plugins.groovy.debugger;

import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileTypeLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * @author peter
 */
public class GroovyHotSwapper extends GenericDebuggerRunner {
  public boolean canRun(@NotNull final String executorId, @NotNull final RunProfile profile) {
    if (!executorId.equals(DefaultDebugExecutor.EXECUTOR_ID)) {
      return false;
    }

    if ("false".equals(System.getProperty("enable.groovy.hotswap", "true"))) {
      return false;
    }

    return true;
  }

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

  @NotNull
  public String getRunnerId() {
    return "GroovyHotSwapper";
  }

  @Override
  protected RunContentDescriptor createContentDescriptor(Project project,
                                                         Executor executor,
                                                         RunProfileState state,
                                                         RunContentDescriptor contentToReuse,
                                                         ExecutionEnvironment env) throws ExecutionException {
    if (state instanceof JavaCommandLine && containsGroovyClasses(project)) {
      final JavaParameters params = ((JavaCommandLine)state).getJavaParameters();
      params.getVMParametersList().add("-javaagent:" + getAgentJarPath());
    }

    return super.createContentDescriptor(project, executor, state, contentToReuse, env);
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
