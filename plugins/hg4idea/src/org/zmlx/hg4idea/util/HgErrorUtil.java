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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.execution.HgCommandResult;

import java.util.List;

public final class HgErrorUtil {

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
}
