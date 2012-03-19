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
public class AndroidRunDraw9PatchAction extends AndroidRunSdkToolAction {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.actions.AndroidRunHierarchyViewerAction");

  public AndroidRunDraw9PatchAction() {
    super(AndroidBundle.message("android.launch.draw.9.patch.action"));
  }

  @Override
  protected void doRunTool(@NotNull Project project, @NotNull final String sdkPath) {
    final String toolPath = sdkPath + File.separator + AndroidCommonUtils.toolPath(getDraw9PatchCmdName());
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
  private static String getDraw9PatchCmdName() {
    return SystemInfo.isWindows ? "draw9patch.bat" : "draw9patch";
  }
}
