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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.execution.HgCommandResult;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HgErrorUtil {

  private static final Logger LOG = Logger.getInstance(HgErrorUtil.class.getName());

  private static final String MERGE_WITH_ANCESTOR_ERROR = "merging with a working directory ancestor has no effect";
  private static final String NOTHING_TO_REBASE_WARNING = "nothing to rebase";

  private HgErrorUtil() {
  }

  public static boolean isAbort(@Nullable HgCommandResult result) {
    return result == null || getAbortLine(result) != null;
  }

  @Nullable
  private static String getAbortLine(@NotNull HgCommandResult result) {
    final List<String> errorLines = result.getErrorLines();
    return ContainerUtil.find(errorLines, s -> isAbortLine(s));
  }

  public static boolean isAncestorMergeError(@Nullable HgCommandResult result) {
    if (result == null) return false;
    String errorLine = getAbortLine(result);
    return errorLine != null && StringUtil.contains(errorLine, MERGE_WITH_ANCESTOR_ERROR);
  }

  public static boolean isNothingToRebase(@Nullable HgCommandResult result) {
    if (result == null) return false;
    return ContainerUtil.exists(result.getOutputLines(), s -> StringUtil.contains(s, NOTHING_TO_REBASE_WARNING));
  }

  public static boolean isAuthorizationError(@Nullable HgCommandResult result) {
    if (result == null) {
      return false;
    }
    String line = getLastErrorLine(result);
    return isAuthorizationError(line);
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

  //unresolved conflict errors included
  public static boolean hasErrorsInCommandExecution(@Nullable HgCommandResult result) {
    return isAbort(result) || result.getExitValue() != 0;
  }

  //todo should be modified and/or merged with HgErrorHandler
  public static boolean isCommandExecutionFailed(@Nullable HgCommandResult result) {
    return isAbort(result) || result.getExitValue() > 1;
  }

  public static boolean hasAuthorizationInDestinationPath(@Nullable String destinationPath) {
    if (StringUtil.isEmptyOrSpaces(destinationPath)) {
      return false;
    }
    return HgUtil.URL_WITH_PASSWORD.matcher(destinationPath).matches();
  }

  public static boolean isUnknownEncodingError(@NotNull List<String> errorLines) {
    if (errorLines.isEmpty()) {
      return false;
    }
    String line = errorLines.get(0);
    return !StringUtil.isEmptyOrSpaces(line) && (line.contains("abort") && line.contains("unknown encoding"));
  }

  //during update  or revert action with  uncommitted merges/changes
  public static boolean hasUncommittedChangesConflict(@Nullable HgCommandResult result) {
    if (result == null) {
      return false;
    }
    // error messages from mercurial after update command failed: "abort: outstanding uncommitted merges", "abort: uncommitted changes";
    //after revert command failed: abort: "uncommitted merge"
    final Pattern UNCOMMITTED_PATTERN = Pattern.compile(".*abort.*uncommitted\\s*(change|merge).*", Pattern.DOTALL);
    Matcher matcher = UNCOMMITTED_PATTERN.matcher(result.getRawError());
    return matcher.matches();
  }

  public static boolean isAuthorizationError(String line) {
    return !StringUtil.isEmptyOrSpaces(line) && (line.contains("authorization required") || line.contains("authorization failed"));
  }

  public static boolean isAbortLine(String line) {
    return !StringUtil.isEmptyOrSpaces(line) && line.trim().startsWith("abort:");
  }

  public static void handleException(@Nullable Project project, @NotNull Exception e) {
    handleException(project, "Error", e);
  }

  public static void handleException(@Nullable Project project, @NotNull String title, @NotNull Exception e) {
    LOG.info(e);
    new HgCommandResultNotifier(project).notifyError(null, title, e.getMessage());
  }

  @Deprecated
  public static void markDirtyAndHandleErrors(Project project, VirtualFile repository) {
    HgUtil.markDirectoryDirty(project, repository);
  }
}
