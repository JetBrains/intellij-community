package org.jetbrains.android.actions;

import com.intellij.CommonBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidRunDdmsAction extends AndroidRunSdkToolAction {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.actions.AndroidRunDdmsAction");
  private static volatile OSProcessHandler ourProcessHandler;

  public AndroidRunDdmsAction() {
    super(AndroidBundle.message("android.launch.ddms.title"));
  }

  @Override
  protected void doRunTool(@NotNull final Project project, @NotNull final String sdkPath) {
    if (getDdmsProcessHandler() != null) {
      Messages.showErrorDialog(project, AndroidBundle.message("android.launch.ddms.already.launched.error"), CommonBundle.getErrorTitle());
      return;
    }
    final boolean adbServiceEnabled = AndroidEnableAdbServiceAction.isAdbServiceEnabled();
    if (adbServiceEnabled && !AndroidEnableAdbServiceAction.disableAdbService(project)) {
      return;
    }

    final String toolPath = sdkPath + File.separator + AndroidCommonUtils.toolPath(getDdmsCmdName());
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(toolPath);
    LOG.info(commandLine.getCommandLineString());

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        doLaunchDdms(commandLine, project, adbServiceEnabled);
      }
    });
  }

  private static void doLaunchDdms(GeneralCommandLine commandLine, final Project project, final boolean adbServiceWasEnabled) {
    try {
      ourProcessHandler = new OSProcessHandler(commandLine.createProcess(), "");
      ourProcessHandler.startNotify();
      ourProcessHandler.waitFor();
    }
    catch (ExecutionException e) {
      LOG.info(e);
    }
    finally {
      ourProcessHandler = null;

      if (adbServiceWasEnabled) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            AndroidEnableAdbServiceAction.setAdbServiceEnabled(project, true);
          }
        });
      }
    }
  }

  @Nullable
  public static OSProcessHandler getDdmsProcessHandler() {
    return ourProcessHandler;
  }

  @NotNull
  private static String getDdmsCmdName() {
    final String monitorCmdName = SystemInfo.isWindows ? "monitor.exe" : "monitor";
    final String archName = SystemInfo.OS_ARCH.equalsIgnoreCase("x86_64") ||
                            SystemInfo.OS_ARCH.equalsIgnoreCase("amd64")
                            ? "x86_64" : "x86";
    return "/lib/monitor-" + archName + "/" + monitorCmdName;
  }
}
