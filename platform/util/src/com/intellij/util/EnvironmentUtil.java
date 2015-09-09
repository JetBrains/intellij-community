/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.FixedFuture;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class EnvironmentUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.EnvironmentUtil");

  private static final int SHELL_ENV_READING_TIMEOUT = 20000;

  private static final Future<Map<String, String>> ourEnvGetter;
  static {
    if (SystemInfo.isMac && "unlocked".equals(System.getProperty("__idea.mac.env.lock")) && Registry.is("idea.fix.mac.env")) {
      ExecutorService executor = Executors.newSingleThreadExecutor(ConcurrencyUtil.newNamedThreadFactory("Shell Env Loader"));
      ourEnvGetter = executor.submit(new Callable<Map<String, String>>() {
        @Override
        public Map<String, String> call() throws Exception {
          return getShellEnv();
        }
      });
      executor.shutdown();
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
      return Collections.unmodifiableMap(new THashMap<String, String>(System.getenv(), CaseInsensitiveStringHashingStrategy.INSTANCE));
    }
    else {
      return System.getenv();
    }
  }

  private EnvironmentUtil() { }

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
   * sane alternatives. This causes a lot of user complains about tools working in a terminal not working when launched
   * from the IDE. To ease their pain, the IDE loads a shell environment (see {@link #getShellEnv()} for gory details)
   * and returns it as the result.
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
   */
  @Nullable
  public static String getValue(@NotNull String name) {
    return getEnvironmentMap().get(name);
  }

  public static String[] getEnvironment() {
    return flattenEnvironment(getEnvironmentMap());
  }

  public static String[] flattenEnvironment(Map<String, String> environment) {
    String[] array = new String[environment.size()];
    int i = 0;
    for (Map.Entry<String, String> entry : environment.entrySet()) {
      array[i++] = entry.getKey() + "=" + entry.getValue();
    }
    return array;
  }

  private static Map<String, String> getShellEnv() throws Exception {
    String shell = System.getenv("SHELL");
    if (shell == null || !new File(shell).canExecute()) {
      throw new Exception("shell:" + shell);
    }

    File reader = FileUtil.findFirstThatExist(
      PathManager.getBinPath() + "/printenv.py",
      PathManager.getHomePath() + "/community/bin/mac/printenv.py",
      PathManager.getHomePath() + "/bin/mac/printenv.py"
    );
    if (reader == null) {
      throw new Exception("bin:" + PathManager.getBinPath());
    }

    File envFile = FileUtil.createTempFile("intellij-shell-env.", ".tmp", false);
    try {
      String[] command = {shell, "-l", "-i", "-c", ("'" + reader.getAbsolutePath() + "' '" + envFile.getAbsolutePath() + "'")};
      LOG.info("loading shell env: " + StringUtil.join(command, " "));

      Process process = Runtime.getRuntime().exec(command);
      int rv = waitAndTerminateAfter(process, SHELL_ENV_READING_TIMEOUT);

      String lines = FileUtil.loadFile(envFile);
      if (rv != 0 || lines.isEmpty()) {
        throw new Exception("rv:" + rv + " text:" + lines.length());
      }
      return parseEnv(lines);
    }
    finally {
      FileUtil.delete(envFile);
    }
  }

  private static Map<String, String> parseEnv(String text) throws Exception {
    Set<String> toIgnore = new HashSet<String>(Arrays.asList("_", "PWD", "SHLVL"));
    Map<String, String> env = System.getenv();
    Map<String, String> newEnv = new HashMap<String, String>();

    String[] lines = text.split("\0");
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
    return Collections.unmodifiableMap(newEnv);
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
      catch (IllegalThreadStateException ignore) { }
    }
    return null;
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
      return parseEnv(lines);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
