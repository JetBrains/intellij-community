/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.execution.process.UnixProcessManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.FixedFuture;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.BaseOutputReader;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import static java.util.Collections.unmodifiableMap;

public class EnvironmentUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.EnvironmentUtil");

  private static final int SHELL_ENV_READING_TIMEOUT = 20000;

  private static final String LANG = "LANG";
  private static final String LC_ALL = "LC_ALL";
  private static final String LC_CTYPE = "LC_CTYPE";

  private static final Future<Map<String, String>> ourEnvGetter;

  static {
    if (SystemInfo.isMac &&
        "unlocked".equals(System.getProperty("__idea.mac.env.lock")) &&
        SystemProperties.getBooleanProperty("idea.fix.mac.env", true)) {
      ourEnvGetter = AppExecutorUtil.getAppExecutorService().submit(new Callable<Map<String, String>>() {
        @Override
        public Map<String, String> call() throws Exception {
          return unmodifiableMap(setCharsetVar(getShellEnv()));
        }
      });
    }
    else {
      ourEnvGetter = new FixedFuture<Map<String, String>>(getSystemEnv());
    }
  }

  private static final NotNullLazyValue<Map<String, String>> ourEnvironment = new AtomicNotNullLazyValue<Map<String, String>>() {
    @NotNull
    @Override
    protected Map<String, String> compute() {
      try {
        return ourEnvGetter.get();
      }
      catch (Throwable t) {
        LOG.warn("can't get shell environment", t);
        return getSystemEnv();
      }
    }
  };

  private static Map<String, String> getSystemEnv() {
    if (SystemInfo.isWindows) {
      return unmodifiableMap(new THashMap<String, String>(System.getenv(), CaseInsensitiveStringHashingStrategy.INSTANCE));
    }
    else {
      return System.getenv();
    }
  }

  private EnvironmentUtil() {
  }

  public static boolean isEnvironmentReady() {
    return ourEnvGetter.isDone();
  }

  /**
   * A wrapper layer around {@link System#getenv()}.
   * <p>
   * On Windows, the returned map is case-insensitive (i.e. {@code map.get("Path") == map.get("PATH")} holds).
   * <p>
   * On Mac OS X things are complicated.<br/>
   * An app launched by a GUI launcher (Finder, Dock, Spotlight etc.) receives a pretty empty and useless environment,
   * since standard Unix ways of setting variables via e.g. ~/.profile do not work. What's more important, there are no
   * sane alternatives. This causes a lot of user complaints about tools working in a terminal not working when launched
   * from the IDE. To ease their pain, the IDE loads a shell environment (see {@link #getShellEnv()} for gory details)
   * and returns it as the result.<br/>
   * And one more thing (c): locale variables on OS X are usually set by a terminal app - meaning they are missing
   * even from a shell environment above. This again causes user complaints about tools being unable to output anything
   * outside ASCII range when launched from the IDE. Resolved by adding LC_CTYPE variable to the map if it doesn't contain
   * explicitly set locale variables (LANG/LC_ALL/LC_CTYPE). See {@link #setCharsetVar(Map)} for details.
   *
   * @return unmodifiable map of the process environment.
   */
  @NotNull
  public static Map<String, String> getEnvironmentMap() {
    return ourEnvironment.getValue();
  }

  /**
   * Same as {@code getEnvironmentMap().get(name)}.
   * Returns value for the passed environment variable name, or null if no such variable found.
   *
   * @see #getEnvironmentMap()
   */
  @Nullable
  public static String getValue(@NotNull String name) {
    return getEnvironmentMap().get(name);
  }

  /**
   * Same as {@code flattenEnvironment(getEnvironmentMap())}.
   * Returns an environment as an array of "NAME=VALUE" strings.
   *
   * @see #getEnvironmentMap()
   */
  public static String[] getEnvironment() {
    return flattenEnvironment(getEnvironmentMap());
  }

  public static String[] flattenEnvironment(@NotNull Map<String, String> environment) {
    String[] array = new String[environment.size()];
    int i = 0;
    for (Map.Entry<String, String> entry : environment.entrySet()) {
      array[i++] = entry.getKey() + "=" + entry.getValue();
    }
    return array;
  }

  private static final String DISABLE_OMZ_AUTO_UPDATE = "DISABLE_AUTO_UPDATE";
  private static final String INTELLIJ_ENVIRONMENT_READER = "INTELLIJ_ENVIRONMENT_READER";

  private static Map<String, String> getShellEnv() throws Exception {
    return new ShellEnvReader().readShellEnv();
  }


  public static class ShellEnvReader {

    public Map<String, String> readShellEnv() throws Exception {
      File reader = PathManager.findBinFileWithException("printenv.py");

      File envFile = FileUtil.createTempFile("intellij-shell-env.", ".tmp", false);
      try {
        List<String> command = getShellProcessCommand();

        int idx = command.indexOf("-c");
        if (idx>=0) {
          // if there is already a command append command to the end
          command.set(idx + 1, command.get(idx+1) + ";" + "'" + reader.getAbsolutePath() + "' '" + envFile.getAbsolutePath() + "'");
        } else {
          command.add("-c");
          command.add("'" + reader.getAbsolutePath() + "' '" + envFile.getAbsolutePath() + "'");
        }

        LOG.info("loading shell env: " + StringUtil.join(command, " "));

        return dumpProcessEnvToFile(command, envFile, "\0");
      }
      finally {
        FileUtil.delete(envFile);
      }
    }

    @NotNull
    protected Map<String, String> dumpProcessEnvToFile(@NotNull List<String> command, @NotNull File envFile, String lineSeparator)
      throws Exception {
      return runProcessAndReadEnvs(command, envFile, lineSeparator);
    }

    @NotNull
    protected static Map<String, String> runProcessAndReadEnvs(@NotNull List<String> command, @NotNull File envFile, String lineSeparator)
      throws Exception {
      return runProcessAndReadEnvs(command, null, envFile, lineSeparator);
    }

    @NotNull
    protected static Map<String, String> runProcessAndReadEnvs(@NotNull List<String> command,
                                                               @Nullable File workingDir,
                                                               @NotNull File envFile,
                                                               String lineSeparator) throws Exception {
      return runProcessAndReadEnvs(command, workingDir, null, envFile, lineSeparator);
    }

    @NotNull
    protected static Map<String, String> runProcessAndReadEnvs(@NotNull List<String> command,
                                                               @Nullable File workingDir,
                                                               @Nullable Map<String, String> envs,
                                                               @NotNull File envFile,
                                                               String lineSeparator) throws Exception {
      ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
      if (envs != null) {
        // we might need default environment for the process to launch correctly
        builder.environment().putAll(envs);
      }
      if (workingDir != null) builder.directory(workingDir);
      builder.environment().put(DISABLE_OMZ_AUTO_UPDATE, "true");
      builder.environment().put(INTELLIJ_ENVIRONMENT_READER, "true");
      Process process = builder.start();
      StreamGobbler gobbler = new StreamGobbler(process.getInputStream());
      int rv = waitAndTerminateAfter(process, SHELL_ENV_READING_TIMEOUT);
      gobbler.stop();

      String lines = FileUtil.loadFile(envFile);
      if (rv != 0 || lines.isEmpty()) {
        throw new Exception("rv:" + rv + " text:" + lines.length() + " out:" + StringUtil.trimEnd(gobbler.getText(), '\n'));
      }
      return parseEnv(lines, lineSeparator);
    }

    @NotNull
    protected List<String> getShellProcessCommand() throws Exception {
      String shell = getShell();
      if (shell == null || !new File(shell).canExecute()) {
        throw new Exception("shell:" + shell);
      }
      List<String> commands = ContainerUtil.newArrayList(shell);
      if (!shell.endsWith("/tcsh") && !shell.endsWith("/csh")) {
        // Act as a login shell
        // tsch does allow to use -l with any other options
        commands.add("-l");
      }
      commands.add("-i"); // enable interactive shell
      return commands;
    }

    @Nullable
    protected String getShell() throws Exception {
      return System.getenv("SHELL");
    }
  }

  @NotNull
  private static Map<String, String> parseEnv(String text, String lineSeparator) throws Exception {
    Set<String> toIgnore = new HashSet<String>(Arrays.asList("_", "PWD", "SHLVL", DISABLE_OMZ_AUTO_UPDATE, INTELLIJ_ENVIRONMENT_READER));
    Map<String, String> env = System.getenv();
    Map<String, String> newEnv = new HashMap<String, String>();

    String[] lines = text.split(lineSeparator);
    for (String line : lines) {
      int pos = line.indexOf('=');
      if (pos <= 0) {
        throw new Exception("malformed:" + line);
      }
      String name = line.substring(0, pos);
      if (!toIgnore.contains(name)) {
        newEnv.put(name, line.substring(pos + 1));
      }
      else if (env.containsKey(name)) {
        newEnv.put(name, env.get(name));
      }
    }

    LOG.info("shell environment loaded (" + newEnv.size() + " vars)");
    return newEnv;
  }

  private static int waitAndTerminateAfter(@NotNull Process process, int timeoutMillis) {
    Integer exitCode = waitFor(process, timeoutMillis);
    if (exitCode != null) {
      return exitCode;
    }
    LOG.warn("shell env loader is timed out");
    UnixProcessManager.sendSigIntToProcessTree(process);
    exitCode = waitFor(process, 1000);
    if (exitCode != null) {
      return exitCode;
    }
    LOG.warn("failed to terminate shell env loader process gracefully, terminating forcibly");
    UnixProcessManager.sendSigKillToProcessTree(process);
    exitCode = waitFor(process, 1000);
    if (exitCode != null) {
      return exitCode;
    }
    LOG.warn("failed to kill shell env loader");
    return -1;
  }

  @Nullable
  private static Integer waitFor(@NotNull Process process, int timeoutMillis) {
    long stop = System.currentTimeMillis() + timeoutMillis;
    while (System.currentTimeMillis() < stop) {
      TimeoutUtil.sleep(100);
      try {
        return process.exitValue();
      }
      catch (IllegalThreadStateException ignore) {
      }
    }
    return null;
  }

  private static Map<String, String> setCharsetVar(@NotNull Map<String, String> env) {
    if (!isCharsetVarDefined(env)) {
      String value = setLocaleEnv(env, CharsetToolkit.getDefaultSystemCharset());
      LOG.info("LC_CTYPE=" + value);
    }
    return env;
  }

  @NotNull
  public static String setLocaleEnv(@NotNull Map<String, String> env, @NotNull Charset charset) {
    Locale locale = Locale.getDefault();
    String language = locale.getLanguage();
    String country = locale.getCountry();
    String value = (language.isEmpty() || country.isEmpty() ? "en_US" : language + '_' + country) + '.' + charset.name();
    env.put(LC_CTYPE, value);
    return value;
  }

  private static boolean isCharsetVarDefined(@NotNull Map<String, String> env) {
    return !env.isEmpty() && (env.containsKey(LANG) || env.containsKey(LC_ALL) || env.containsKey(LC_CTYPE));
  }

  public static void inlineParentOccurrences(@NotNull Map<String, String> envs) {
    Map<String, String> parentParams = new HashMap<String, String>(System.getenv());
    for (Map.Entry<String, String> entry : envs.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (value != null) {
        String parentVal = parentParams.get(key);
        if (parentVal != null && containsEnvKeySubstitution(key, value)) {
          envs.put(key, value.replace("$" + key + "$", parentVal));
        }
      }
    }
  }

  private static boolean containsEnvKeySubstitution(final String envKey, final String val) {
    return ArrayUtil.find(val.split(File.pathSeparator), "$" + envKey + "$") != -1;
  }

  @TestOnly
  static Map<String, String> testLoader() {
    try {
      return getShellEnv();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @TestOnly
  static Map<String, String> testParser(@NotNull String lines) {
    try {
      return parseEnv(lines, "\0");
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static class StreamGobbler extends BaseOutputReader {
    private static final Options OPTIONS = new Options() {
      @Override
      public SleepingPolicy policy() {
        return SleepingPolicy.BLOCKING;
      }

      @Override
      public boolean splitToLines() {
        return false;
      }
    };

    private final StringBuffer myBuffer;

    public StreamGobbler(@NotNull InputStream stream) {
      super(stream, CharsetToolkit.getDefaultSystemCharset(), OPTIONS);
      myBuffer = new StringBuffer();
      start("stdout/stderr streams of shell env loading process");
    }

    @NotNull
    @Override
    protected Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
      return AppExecutorUtil.getAppExecutorService().submit(runnable);
    }

    @Override
    protected void onTextAvailable(@NotNull String text) {
      myBuffer.append(text);
    }

    @NotNull
    public String getText() {
      return myBuffer.toString();
    }
  }
}