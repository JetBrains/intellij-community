// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.execution.HgCommandResult;

import javax.swing.event.HyperlinkEvent;
import java.util.List;

public final class HgErrorUtil {

  public static final String SETTINGS_LINK = "settings";
  public static final String MAPPING_ERROR_MESSAGE = String.format(
    "Please, ensure that your project base dir is hg root directory or specify full repository path in  <a href='" +
    SETTINGS_LINK +
    "'>directory mappings panel</a>.");

  private HgErrorUtil() { }

  public static boolean isAbort(@Nullable HgCommandResult result) {
    if (result == null) {
      return true;
    }
    final List<String> errorLines = result.getErrorLines();
    for (String line : errorLines) {
      if (!StringUtil.isEmptyOrSpaces(line) && line.trim().startsWith("abort:")) {
        return true;
      }
    }
    return false;
  }

  public static boolean isAuthorizationError(@Nullable HgCommandResult result) {
    if (result == null) {
      return false;
    }
    String line = getLastErrorLine(result);
    return !StringUtil.isEmptyOrSpaces(line) && (line.contains("authorization required") || line.contains("authorization failed")
    );
  }

  @Nullable
  private static String getLastErrorLine(@Nullable HgCommandResult result) {
    if (result == null) {
      return null;
    }
    final List<String> errorLines = result.getErrorLines();
    if (errorLines.isEmpty()) {
      return null;
    }
    return errorLines.get(errorLines.size() - 1);
  }

  public static boolean hasErrorsInCommandExecution(@Nullable HgCommandResult result) {
    return isAbort(result) || result.getExitValue() != 0;
  }

  public static boolean hasAuthorizationInDestinationPath(@Nullable String destinationPath) {
    if (StringUtil.isEmptyOrSpaces(destinationPath)) {
      return false;
    }
    return HgUtil.URL_WITH_PASSWORD.matcher(destinationPath).matches();
  }

  @NotNull
  public static NotificationListener getMappingErrorNotificationListener(@NotNull final Project project) {
    return new NotificationListener.Adapter() {
      @Override
      protected void hyperlinkActivated(@NotNull Notification notification,
                                        @NotNull HyperlinkEvent e) {
        if (SETTINGS_LINK.equals(e.getDescription())) {
          ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, VcsBundle.message("version.control.main.configurable.name"));
        }
      }
    };
  }

  public static boolean isUnknownEncodingError(@Nullable HgCommandResult result) {
    if (result == null) {
      return false;
    }
    List<String> errorLines = result.getErrorLines();
    if (errorLines.isEmpty()) {
      return false;
    }
    String line = errorLines.get(0);
    return !StringUtil.isEmptyOrSpaces(line) && (line.contains("abort") && line.contains("unknown encoding"));
  }
}
