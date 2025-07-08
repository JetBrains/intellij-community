// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.system.OS;
import kotlinx.coroutines.CompletableDeferred;
import kotlinx.coroutines.CompletableDeferredKt;
import kotlinx.coroutines.future.FutureKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EnvironmentUtil {
  private static final String DESKTOP_STARTUP_ID = "DESKTOP_STARTUP_ID";
  private static final String MAC_OS_LOCALE_PATH = "/usr/share/locale";

  /** @deprecated primitive well-known constant; inline */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @SuppressWarnings("DeprecatedIsStillUsed")
  public static final String DISABLE_OMZ_AUTO_UPDATE = "DISABLE_AUTO_UPDATE";
  /** @deprecated primitive well-known constant; inline */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final String BASH_EXECUTABLE_NAME = "bash";
  /** @deprecated primitive well-known constant; inline */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final String SHELL_VARIABLE_NAME = "SHELL";
  /** @deprecated primitive well-known constant; inline */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final String SHELL_LOGIN_ARGUMENT = "-l";
  /** @deprecated primitive well-known constant; inline */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final String SHELL_COMMAND_ARGUMENT = "-c";
  /** @deprecated non-standard command (POSIX specifies '.'); do not use */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final String SHELL_SOURCE_COMMAND = "source";
  /** @deprecated primitive well-known constant; inline */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final String SHELL_ENV_COMMAND = "/usr/bin/env";
  /** @deprecated primitive well-known constant; inline */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final String ENV_ZERO_ARGUMENT = "-0";
  /** @deprecated implementation detail; do not use */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final String MacOS_LOADER_BINARY = "printenv";

  private static final AtomicReference<CompletableDeferred<Map<String, String>>> ourEnvGetter = new AtomicReference<>();

  private EnvironmentUtil() { }

  /**
   * <p>A wrapper layer around {@link System#getenv()}.</p>
   *
   * <p>On Windows, the returned map is case-insensitive (i.e. {@code map.get("Path") == map.get("PATH")} holds).</p>
   *
   * <p>On macOS, things are complicated.<br/>
   * An app launched by a GUI launcher (Finder, Dock, Spotlight etc.) receives a pretty empty and useless environment,
   * since standard Unix ways of setting variables via e.g. {@code ~/.profile} do not work. What's more important, there are no
   * sane alternatives. This causes a lot of user complaints about tools working in a terminal not working when launched
   * from the IDE. To ease their pain, the IDE loads a shell environment
   * (see {@link com.intellij.platform.ide.bootstrap.StartupUtil#shouldLoadShellEnv} for gory details)
   * and returns it as the result.<br/>
   * And one more thing (c): locale variables on macOS are usually set by a terminal app - meaning they are missing
   * even from a shell environment above. This again causes user complaints about tools being unable to output anything
   * outside ASCII range when launched from the IDE. Resolved by adding LC_CTYPE variable to the map if it doesn't contain
   * explicitly set locale variables (LANG/LC_ALL/LC_CTYPE).<br/>
   * <b>Note:</b> this call may block until the environment is loaded.</p>
   *
   * @return unmodifiable map of the process environment.
   */
  public static @NotNull Map<String, String> getEnvironmentMap() {
    CompletableDeferred<Map<String, String>> getter = ourEnvGetter.get();
    if (getter == null) {
      getter = CompletableDeferredKt.CompletableDeferred(getSystemEnv());
      if (!ourEnvGetter.compareAndSet(null, getter)) {
        getter = ourEnvGetter.get();
      }
    }
    try {
      Map<String, String> result = getter.isCompleted() ? getter.getCompleted() : FutureKt.asCompletableFuture(getter).join();
      if (result.isEmpty()) {
        ourEnvGetter.set(CompletableDeferredKt.CompletableDeferred(result = getSystemEnv()));  // loading failed
      }
      return result;
    }
    catch (Throwable t) {
      throw new AssertionError(t);  // unknown state; not expected to happen
    }
  }

  @ApiStatus.Internal
  public static void setEnvironmentLoader(@NotNull CompletableDeferred<Map<String, String>> loader) {
    ourEnvGetter.set(loader);
  }

  private static Map<String, String> getSystemEnv() {
    if (OS.CURRENT == OS.Windows) {
      return Collections.unmodifiableMap(CollectionFactory.createCaseInsensitiveStringMap(System.getenv()));
    }
    else if (OS.isGenericUnix()) {
      // DESKTOP_STARTUP_ID variable can be set by an application launcher in X Window environment.
      // It shouldn't be passed to child processes as per 'Startup notification protocol'
      // (https://specifications.freedesktop.org/startup-notification-spec/startup-notification-latest.txt).
      // Ideally, JDK should clear this variable, and it actually does, but the snapshot of the environment variables,
      // returned by `System#getenv`, is captured before the removal.
      Map<String, String> env = System.getenv();
      if (env.containsKey(DESKTOP_STARTUP_ID)) {
        env = new HashMap<>(env);
        env.remove(DESKTOP_STARTUP_ID);
        env = Collections.unmodifiableMap(env);
      }
      return env;
    }
    else {
      return System.getenv();
    }
  }

  /**
   * Same as {@code getEnvironmentMap().get(name)}.
   * Returns value for the passed environment variable name, or {@code null} if no such variable was found.
   *
   * @see #getEnvironmentMap()
   */
  public static @Nullable String getValue(@NotNull String name) {
    return getEnvironmentMap().get(name);
  }

  /**
   * Validates environment variable name in accordance to
   * {@code ProcessEnvironment#validateVariable} ({@code ProcessEnvironment#validateName} on Windows).
   *
   * @see #isValidValue(String)
   * @see <a href="http://pubs.opengroup.org/onlinepubs/000095399/basedefs/xbd_chap08.html">Environment Variables in Unix</a>
   * @see <a href="https://docs.microsoft.com/en-us/windows/desktop/ProcThread/environment-variables">Environment Variables in Windows</a>
   */
  @Contract(value = "null -> false", pure = true)
  public static boolean isValidName(@Nullable String name) {
    return name != null && !name.isEmpty() && name.indexOf('\0') == -1 && name.indexOf('=', OS.CURRENT == OS.Windows ? 1 : 0) == -1;
  }

  /**
   * Validates environment variable value in accordance to {@code ProcessEnvironment#validateValue}.
   *
   * @see #isValidName(String)
   */
  @Contract(value = "null -> false", pure = true)
  public static boolean isValidValue(@Nullable String value) {
    return value != null && value.indexOf('\0') == -1;
  }

  public static @NotNull Map<String, String> parseEnv(String @NotNull [] lines) {
    @SuppressWarnings("SSBasedInspection")
    Set<String> toIgnore = new HashSet<>(Arrays.asList("_", "PWD", "SHLVL"));
    Map<String, String> env = System.getenv();
    Map<String, String> newEnv = new HashMap<>();

    for (String line : lines) {
      if (!line.isEmpty()) {
        int pos = line.indexOf('=');
        if (pos <= 0) throw new IllegalArgumentException("malformed: '" + line + "'");
        String name = line.substring(0, pos);
        if (!toIgnore.contains(name)) {
          newEnv.put(name, line.substring(pos + 1));
        }
        else if (env.containsKey(name)) {
          newEnv.put(name, env.get(name));
        }
      }
    }

    return newEnv;
  }

  private static boolean checkIfLocaleAvailable(String candidateLanguageTerritory) {
    return ContainerUtil.exists(Locale.getAvailableLocales(), l -> Objects.equals(l.toString(), candidateLanguageTerritory)) &&
           (OS.CURRENT != OS.macOS || Files.exists(Paths.get(MAC_OS_LOCALE_PATH, candidateLanguageTerritory)));
  }

  public static @NotNull String setLocaleEnv(@NotNull Map<String, String> env, @NotNull Charset charset) {
    Locale locale = Locale.getDefault();
    String language = locale.getLanguage();
    String country = locale.getCountry();

    String languageTerritory = "en_US";
    if (!language.isEmpty() && !country.isEmpty()) {
      String languageTerritoryFromLocale = language + '_' + country;
      if (checkIfLocaleAvailable(languageTerritoryFromLocale)) {
        languageTerritory = languageTerritoryFromLocale ;
      }
    }

    String result = languageTerritory + '.' + charset.name();
    env.put("LC_CTYPE", result);
    return result;
  }

  public static void inlineParentOccurrences(@NotNull Map<String, String> envs) {
    inlineParentOccurrences(envs, getEnvironmentMap());
  }

  private static final Pattern pattern = Pattern.compile("\\$(.*?)\\$");

  public static void inlineParentOccurrences(@NotNull Map<String, String> envs, @NotNull Map<String, String> parentEnv) {
    LinkedHashMap<String, String> lookup = new LinkedHashMap<>(envs);
    lookup.putAll(parentEnv);
    for (Map.Entry<String, String> entry : envs.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (value != null) {
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
          String group = matcher.group(1);
          String expanded = lookup.get(group);
          if (expanded != null) {
            value = value.replace("$" + group + "$", expanded);
          }
        }
        envs.put(key, value);
        lookup.put(key, value);
      }
    }
  }

  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  public static boolean containsEnvKeySubstitution(final String envKey, final String val) {
    return ArrayUtil.find(val.split(java.io.File.pathSeparator), "$" + envKey + "$") != -1;
  }

  @ApiStatus.Internal
  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  public static void appendSearchPath(@NotNull Map<String, String> env, @NotNull String envName, @NotNull String pathToAppend) {
    String currentPath = env.get(envName);
    String newPath = currentPath != null ? currentPath + java.io.File.pathSeparator + pathToAppend : pathToAppend;
    env.put(envName, newPath);
  }
}
