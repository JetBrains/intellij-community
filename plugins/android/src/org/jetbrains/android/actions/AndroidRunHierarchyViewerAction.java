package org.jetbrains.android.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidRunHierarchyViewerAction extends AndroidRunSdkToolAction {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.actions.AndroidRunHierarchyViewerAction");

  public AndroidRunHierarchyViewerAction() {
    super(AndroidBundle.message("android.launch.hierarchy.viewer.action"));
  }

  @Override
  protected void doRunTool(@NotNull Project project, @NotNull final String sdkPath) {
    final String toolPath = sdkPath + File.separator + AndroidCommonUtils.toolPath(getHierarchyViewerCmdName());
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(toolPath);
    LOG.info(commandLine.getCommandLineString());
    try {
      commandLine.createProcess();
    }
    catch (ExecutionException e) {
      LOG.info(e);
    }
  }

  @NotNull
  private static String getHierarchyViewerCmdName() {
    return SystemInfo.isWindows ? "hierarchyviewer.bat" : "hierarchyviewer";
  }
}
