/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.config;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Map;

import static com.intellij.util.containers.ContainerUtil.newHashSet;

/**
 * Git utilities for working with configuration
 */
public class GitConfigUtil {

  public static final String USER_NAME = "user.name";
  public static final String USER_EMAIL = "user.email";
  public static final String CORE_AUTOCRLF = "core.autocrlf";

  private GitConfigUtil() {
  }

  /**
   * Get configuration values for the repository. Note that the method executes a git command.
   *
   * @param project the context project
   * @param root    the git root
   * @param keyMask the keys to be queried
   * @param result  the map to put results to
   * @throws VcsException if there is a problem with running git
   */
  public static void getValues(Project project, VirtualFile root, String keyMask, Map<String, String> result) throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.CONFIG);
    h.setSilent(true);
    h.addParameters("--null");
    if (keyMask != null) {
      h.addParameters("--get-regexp", keyMask);
    } else {
      h.addParameters("-l");
    }
    String output = Git.getInstance().runCommand(h).getOutputOrThrow();
    int start = 0;
    int pos;
    while ((pos = output.indexOf('\n', start)) != -1) {
      String key = output.substring(start, pos);
      start = pos + 1;
      if ((pos = output.indexOf('\u0000', start)) == -1) {
        break;
      }
      String value = output.substring(start, pos);
      start = pos + 1;
      result.put(key, value);
    }
  }


  /**
   * Get configuration value for the repository. Note that the method executes a git command.
   *
   * @param project the context project
   * @param root    the git root
   * @param key     the keys to be queried
   * @return the value associated with the key or null if the value is not found
   * @throws VcsException an exception
   */
  @Nullable
  public static String getValue(Project project, VirtualFile root, @NonNls String key) throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.CONFIG);
    h.setSilent(true);
    h.ignoreErrorCode(1);
    h.addParameters("--null", "--get", key);
    GitCommandResult result = Git.getInstance().runCommand(h);
    String output = result.getOutputOrThrow();
    int pos = output.indexOf('\u0000');
    if (result.getExitCode() != 0 || pos == -1) {
      return null;
    }
    return output.substring(0, pos);
  }

  /**
   * Converts the git config boolean value (which can be specified in various ways) to Java Boolean.
   * @return true if the value represents "true", false if the value represents "false", null if the value doesn't look like a boolean value.
   */
  @Nullable
  public static Boolean getBooleanValue(@NotNull String value) {
    value = value.toLowerCase(Locale.ENGLISH);
    if (newHashSet("true", "yes", "on", "1").contains(value)) return true;
    if (newHashSet("false", "no", "off", "0", "").contains(value)) return false;
    return null;
  }

  /**
   * Get commit encoding for the specified root
   *
   * @param project the context project
   * @param root    the project root
   * @return the commit encoding or UTF-8 if the encoding is note explicitly specified
   */
  public static String getCommitEncoding(final Project project, VirtualFile root) {
    @NonNls String encoding = null;
    try {
      encoding = getValue(project, root, "i18n.commitencoding");
    }
    catch (VcsException e) {
      // ignore exception
    }
    if (encoding == null || encoding.length() == 0) {
      encoding = CharsetToolkit.UTF8;
    }
    return encoding;
  }

  /**
   * Get log output encoding for the specified root
   *
   * @param project the context project
   * @param root    the project root
   * @return the log output encoding, the commit encoding, or UTF-8 if the encoding is note explicitly specified
   */
  public static String getLogEncoding(final Project project, VirtualFile root) {
    @NonNls String encoding = null;
    try {
      encoding = getValue(project, root, "i18n.logoutputencoding");
    }
    catch (VcsException e) {
      // ignore exception
    }
    if (encoding == null || encoding.length() == 0) {
      encoding = getCommitEncoding(project, root);
    }
    return encoding;
  }

  /**
   * Get encoding that GIT uses for file names.
   *
   * @return the encoding for file names
   */
  public static String getFileNameEncoding() {
    // TODO the best guess is that the default encoding is used.
    return Charset.defaultCharset().name();
  }

  /**
   * Set the value
   *
   * @param project the project
   * @param root    the git root
   * @param key     the key to set
   * @param value   the value to set
   * @throws VcsException if there is a problem with running git
   */
  public static void setValue(Project project, VirtualFile root, String key, String value, String... additionalParameters) throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.CONFIG);
    h.setSilent(true);
    h.ignoreErrorCode(1);
    h.addParameters(additionalParameters);
    h.addParameters(key, value);
    Git.getInstance().runCommand(h).getOutputOrThrow();
  }
}
