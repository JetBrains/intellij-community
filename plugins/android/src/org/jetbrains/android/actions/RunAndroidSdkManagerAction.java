/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.actions;

import com.android.SdkConstants;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.util.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Eugene.Kudelevsky
 */
public class RunAndroidSdkManagerAction extends AndroidRunSdkToolAction {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.actions.RunAndroidSdkManagerAction");

  public RunAndroidSdkManagerAction() {
    super(getName());
  }

  private static String getName() {
    return AndroidBundle.message("android.run.sdk.manager.action.text");
  }

  @Override
  protected void doRunTool(@NotNull final Project project, @NotNull final String sdkPath) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final String toolPath = sdkPath + File.separator + AndroidCommonUtils.toolPath(SdkConstants.androidCmdName());
        GeneralCommandLine commandLine = new GeneralCommandLine();
        commandLine.setExePath(toolPath);
        commandLine.addParameter("sdk");

        final StringBuildingOutputProcessor processor = new StringBuildingOutputProcessor();
        try {
          if (AndroidUtils.executeCommand(commandLine, processor, WaitingStrategies.WaitForTime.getInstance(500)) ==
              ExecutionStatus.TIMEOUT) {
            return;
          }
        }
        catch (ExecutionException e) {
          LOG.error(e);
          return;
        }

        final String message = processor.getMessage();
        if (message.toLowerCase().contains("error")) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              Messages.showErrorDialog(project, "Cannot launch SDK manager.\nOutput:\n" + message, getName());
            }
          });
        }
      }
    });
  }
}
