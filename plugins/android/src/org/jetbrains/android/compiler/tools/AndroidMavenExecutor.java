package org.jetbrains.android.compiler.tools;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineBuilder;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.idea.maven.execution.MavenExternalParameters;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidMavenExecutor {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.tools.AndroidMavenExecutor");

  private static final String BUILD_ERROR_INDICATOR = "[error]";
  private static final String FAILED_TO_RESOLVE_ARTIFACT_INDICATOR = "[info] failed to resolve artifact";

  private AndroidMavenExecutor() {
  }

  public static Map<CompilerMessageCategory, List<String>> generateResources(Module module) {
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(module.getProject());

    assert projectsManager != null;

    MavenRunnerParameters parameters =
      new MavenRunnerParameters(true, projectsManager.findProject(module).getDirectory(),
                                Collections.singletonList("generate-resources"),
                                projectsManager.getExplicitProfiles());

    Map<CompilerMessageCategory, List<String>> result = new HashMap<CompilerMessageCategory, List<String>>();
    result.put(CompilerMessageCategory.ERROR, new ArrayList<String>());

    try {
      JavaParameters javaParams = MavenExternalParameters.createJavaParameters(parameters, projectsManager.getGeneralSettings(),
                                                                               MavenRunner.getInstance(module.getProject()).getSettings());

      GeneralCommandLine commandLine = CommandLineBuilder.createFromJavaParameters(javaParams);

      String[] commands = commandLine.getCommands();
      String command = StringUtil.join(commands, " ");
      LOG.info("Execute: " + command);
      String message = AndroidUtils.executeCommand(commandLine);
      if (message != null) {
        LOG.info(message);
        String lcmessage = message.toLowerCase();
        int buildErrorIndex = lcmessage.indexOf(BUILD_ERROR_INDICATOR);
        if (buildErrorIndex >= 0) {
          int failedToResolveIndex = lcmessage.indexOf(FAILED_TO_RESOLVE_ARTIFACT_INDICATOR);
          /*if (failedToResolveIndex >= 0) {
            result.get(CompilerMessageCategory.ERROR).add("Failed to copy Android resources from Maven artifacts");
          }
          else {*/
            result.get(CompilerMessageCategory.ERROR).add(message.substring(buildErrorIndex));
          //}
        }
      }
    }
    catch (ExecutionException e) {
      LOG.info(e);
      result.get(CompilerMessageCategory.ERROR).add(e.getMessage());
    }

    return result;
  }
}
