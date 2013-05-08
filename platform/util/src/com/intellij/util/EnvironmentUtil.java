/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.FixedFuture;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class EnvironmentUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.EnvironmentUtil");

  private static final int SHELL_ENV_READING_TIMEOUT = 10000;

  private static final Future<Map<String, String>> ourEnvGetter;
  static {
    if (SystemInfo.isMac && Registry.is("idea.fix.mac.env")) {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      ourEnvGetter = executor.submit(new Callable<Map<String, String>>() {
        @Override
        public Map<String, String> call() throws Exception {
          return getShellEnv();
        }
      });
      executor.shutdown();
    }
    else {
      ourEnvGetter = new FixedFuture<Map<String, String>>(System.getenv());
    }
  }

  private static final NotNullLazyValue<Map<String, String>> ourEnvironment = new AtomicNotNullLazyValue<Map<String, String>>() {
    @NotNull
    @Override
    protected Map<String, String> compute() {
      try {
        return ourEnvGetter.get();
      }
      catch (Exception e) {
        LOG.warn(e);
        return System.getenv();
      }
    }
  };

  private static final NotNullLazyValue<Map<String, String>> ourEnvironmentOsSpecific = new AtomicNotNullLazyValue<Map<String, String>>() {
    @NotNull
    @Override
    protected Map<String, String> compute() {
      Map<String, String> env = ourEnvironment.getValue();
      if (SystemInfo.isWindows) {
        env = Collections.unmodifiableMap(new THashMap<String, String>(env, CaseInsensitiveStringHashingStrategy.INSTANCE));
      }
      return env;
    }
  };

  private EnvironmentUtil() { }

  public static boolean isEnvironmentReady() {
    return ourEnvGetter.isDone();
  }

  /**
   * Returns the process environment.
   * On Mac OS X a shell (Terminal.app) environment is returned (unless disabled by a system property).
   *
   * @return unmodifiable map of the process environment.
   */
  @NotNull
  public static Map<String, String> getEnvironmentMap() {
    return ourEnvironment.getValue();
  }

  /**
   * Returns value for the passed environment variable name.
   * The passed environment variable name is handled in a case-sensitive or case-insensitive manner depending on OS.<p>
   * For example, on Windows <code>getValue("Path")</code> will return the same result as <code>getValue("PATH")</code>.
   *
   * @param name environment variable name
   * @return value of the environment variable or null if no such variable found
   */
  @Nullable
  public static String getValue(@NotNull String name) {
    return ourEnvironmentOsSpecific.getValue().get(name);
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

  @SuppressWarnings("SpellCheckingInspection")
  private static Map<String, String> getShellEnv() {
    try {
      String shell = System.getenv("SHELL");
      if (shell == null || !new File(shell).canExecute()) {
        throw new Exception("shell:" + shell);
      }

      String[] command = {shell, "-l", "-c", "/usr/bin/printenv"};
      Process process = Runtime.getRuntime().exec(command);
      ProcessKiller processKiller = new ProcessKiller(process);
      processKiller.killAfter(SHELL_ENV_READING_TIMEOUT);
      List<String> lines = FileUtil.loadLines(process.getInputStream());
      processKiller.stopWaiting();
      int rv = process.waitFor();
      if (rv != 0 || lines.isEmpty()) {
        throw new Exception("rv:" + rv + " lines:" + lines.size());
      }

      Set<String> toIgnore = new HashSet<String>(Arrays.asList("_", "PWD", "SHLVL"));
      Map<String, String> env = System.getenv();
      Map<String, String> newEnv = new HashMap<String, String>();
      for (String line : lines) {
        int pos = line.indexOf('=');
        if (pos <= 0) {
          LOG.warn("malformed:" + line);
          continue;
        }
        String name = line.substring(0, pos);
        if (!toIgnore.contains(name)) {
          newEnv.put(name, line.substring(pos + 1));
        }
        else if (env.containsKey(name)) {
          newEnv.put(name, env.get(name));
        }
      }
      if (newEnv.size() < lines.size() - toIgnore.size()) {
        // some lines weren't parsed - we're better to fall back to original environment than use possibly incomplete one
        throw new Exception("env:" + newEnv.size() + " lines:" + lines.size());
      }

      LOG.info("shell environment loaded (" + newEnv.size() + " vars)");
      return Collections.unmodifiableMap(newEnv);
    }
    catch (Throwable t) {
      LOG.warn("can't get shell environment", t);
      return System.getenv();
    }
  }

  private static class ProcessKiller {
    private final Process myProcess;
    private final Object myWaiter = new Object();

    public ProcessKiller(Process process) {
      myProcess = process;
    }

    public void killAfter(long timeout) {
      final long stop = System.currentTimeMillis() + timeout;
      new Thread() {
        @Override
        public void run() {
          synchronized (myWaiter) {
            while (System.currentTimeMillis() < stop) {
              try {
                myProcess.exitValue();
                break;
              }
              catch (IllegalThreadStateException ignore) { }

              try {
                myWaiter.wait(100);
              }
              catch (InterruptedException ignore) { }
            }
          }

          try {
            myProcess.exitValue();
          }
          catch (IllegalThreadStateException e) {
            UnixProcessManager.sendSigIntToProcessTree(myProcess);
            LOG.warn("timed out");
          }
        }
      }.start();
    }

    public void stopWaiting() {
      synchronized (myWaiter) {
        myWaiter.notifyAll();
      }
    }
  }

  /** @deprecated use {@link #getEnvironmentMap()} (to remove in IDEA 14) */
  @SuppressWarnings({"UnusedDeclaration", "SpellCheckingInspection"})
  public static Map<String, String> getEnviromentProperties() {
    return getEnvironmentMap();
  }

  /** @deprecated use {@link #getEnvironmentMap()} (to remove in IDEA 14) */
  @SuppressWarnings({"UnusedDeclaration", "SpellCheckingInspection"})
  public static Map<String, String> getEnvironmentProperties() {
    return getEnvironmentMap();
  }
}
