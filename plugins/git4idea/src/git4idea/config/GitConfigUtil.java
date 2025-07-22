// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitProjectConfigurationCache;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Git utilities for working with configuration
 */
public final class GitConfigUtil {
  private static final Logger LOG = Logger.getInstance(GitConfigUtil.class);
  private static final Set<String> REPORTED_CUSTOM_ENCODINGS = new CopyOnWriteArraySet<>();

  public static final @NlsSafe String USER_NAME = "user.name";
  public static final @NlsSafe String USER_EMAIL = "user.email";
  public static final @NlsSafe String CORE_AUTOCRLF = "core.autocrlf";
  public static final @NlsSafe String CREDENTIAL_HELPER = "credential.helper";
  public static final @NlsSafe String CORE_SSH_COMMAND = "core.sshCommand";
  public static final @NlsSafe String CORE_COMMENT_CHAR = "core.commentChar";
  public static final @NlsSafe String LOG_OUTPUT_ENCODING = "i18n.logoutputencoding";
  public static final @NlsSafe String COMMIT_ENCODING = "i18n.commitencoding";
  public static final @NlsSafe String COMMIT_TEMPLATE = "commit.template";
  public static final @NlsSafe String GPG_PROGRAM = "gpg.program";
  public static final @NlsSafe String GPG_COMMIT_SIGN = "commit.gpgSign";
  public static final @NlsSafe String GPG_COMMIT_SIGN_KEY = "user.signingkey";

  private GitConfigUtil() {
  }

  public static @NotNull Map<@NotNull String, @NotNull String> getValues(@NotNull Project project,
                                                                         @NotNull VirtualFile root,
                                                                         @Nullable @NonNls String keyMask) throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.CONFIG);
    h.setEnableInteractiveCallbacks(false);
    h.setSilent(true);
    h.addParameters("--null");
    if (keyMask != null) {
      h.addParameters("--get-regexp", keyMask);
    }
    else {
      h.addParameters("-l");
    }
    String output = Git.getInstance().runCommand(h).getOutputOrThrow();
    int start = 0;
    int pos;
    Map<String, String> result = new HashMap<>();
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
    return result;
  }


  public static @Nullable String getValue(@NotNull Project project, @NotNull VirtualFile root, @NotNull @NonNls String key)
    throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.CONFIG);
    return getValue(h, key);
  }

  public static @Nullable String getValue(@NotNull Project project, @NotNull File root, @NotNull @NonNls String key)
    throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.CONFIG);
    return getValue(h, key);
  }

  private static @Nullable String getValue(@NotNull GitLineHandler h, @NotNull @NonNls String key) throws VcsException {
    h.setEnableInteractiveCallbacks(false);
    h.setSilent(true);
    h.addParameters("--null", "--get", key);
    GitCommandResult result = Git.getInstance().runCommand(h);
    String output = result.getOutputOrThrow(1);
    int pos = output.indexOf('\u0000');
    if (result.getExitCode() != 0 || pos == -1) {
      return null;
    }
    return output.substring(0, pos);
  }

  /**
   * Converts the git config boolean value (which can be specified in various ways) to Java Boolean.
   *
   * @return true if the value represents "true", false if the value represents "false", null if the value doesn't look like a boolean value.
   */
  public static @Nullable Boolean getBooleanValue(@Nullable @NonNls String value) {
    if (value == null) return null;
    value = StringUtil.toLowerCase(value);
    if (ContainerUtil.newHashSet("true", "yes", "on", "1").contains(value)) return true;
    if (ContainerUtil.newHashSet("false", "no", "off", "0", "").contains(value)) return false;
    return null;
  }

  /**
   * @deprecated Use {@link #getCommitEncodingCharset(Project, VirtualFile)}
   */
  @Deprecated
  public static @NotNull String getCommitEncoding(@NotNull Project project, @NotNull VirtualFile root) {
    return getCommitEncodingCharset(project, root).name();
  }

  /**
   * @deprecated Use {@link #getLogEncodingCharset(Project, VirtualFile)}
   */
  @Deprecated
  public static String getLogEncoding(@NotNull Project project, @NotNull VirtualFile root) {
    return getLogEncodingCharset(project, root).name();
  }

  /**
   * Get commit encoding for the specified root, or UTF-8 if the encoding is note explicitly specified
   */
  public static @NotNull Charset getCommitEncodingCharset(@NotNull Project project, @NotNull VirtualFile root) {
    try {
      String encoding = getValue(project, root, COMMIT_ENCODING);
      Charset charset = tryParseEncoding(encoding);
      if (charset != null) return charset;
    }
    catch (VcsException ignore) {
    }
    return StandardCharsets.UTF_8;
  }

  public static @NotNull Charset getCommitEncodingCharsetCached(@NotNull Project project, @NotNull VirtualFile root) {
    String encoding = GitProjectConfigurationCache.getInstance(project)
      .readRepositoryConfig(root, COMMIT_ENCODING);
    Charset charset = tryParseEncoding(encoding);
    if (charset != null) return charset;
    return StandardCharsets.UTF_8;
  }

  /**
   * Get log output encoding for the specified root, or UTF-8 if the encoding is note explicitly specified
   */
  public static @NotNull Charset getLogEncodingCharset(@NotNull Project project, @NotNull VirtualFile root) {
    try {
      String encoding = getValue(project, root, LOG_OUTPUT_ENCODING);
      Charset charset = tryParseEncoding(encoding);
      if (charset != null) return charset;
    }
    catch (VcsException ignore) {
    }
    return getCommitEncodingCharset(project, root);
  }

  private static @Nullable Charset tryParseEncoding(@Nullable String encodingName) {
    if (StringUtil.isEmpty(encodingName)) return null;

    try {
      Charset charset = Charset.forName(encodingName);
      if (REPORTED_CUSTOM_ENCODINGS.add(encodingName)) {
        LOG.info("Using a custom encoding " + encodingName);
      }
      return charset;
    }
    catch (IllegalArgumentException ignore) {
      if (REPORTED_CUSTOM_ENCODINGS.add(encodingName)) {
        LOG.warn("Custom encoding " + encodingName + " is not supported, using UTF-8 instead");
      }
      return null;
    }
  }

  public static void setValue(@NotNull Project project,
                              @NotNull VirtualFile root,
                              @NotNull @NonNls String key,
                              @NotNull @NonNls String value,
                              @NonNls String... additionalParameters) throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.CONFIG);
    h.setSilent(false);
    h.addParameters(additionalParameters);
    h.addParameters(key, value);
    Git.getInstance().runCommand(h).throwOnError(1);
  }

  /**
   * Checks that Credential helper is defined in git config.
   */
  public static boolean isCredentialHelperUsed(@NotNull Project project, @NotNull File workingDirectory) {
    try {
      GitLineHandler handler = new GitLineHandler(project, workingDirectory, GitCommand.CONFIG);
      String value = getValue(handler, CREDENTIAL_HELPER);
      return StringUtil.isNotEmpty(value);
    }
    catch (VcsException ignored) {
      return false;
    }
  }
}
