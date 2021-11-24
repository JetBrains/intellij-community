// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@ApiStatus.Internal
@ApiStatus.Experimental
public class EnvironmentRestorer {

  static final String RESERVED_ORIGINAL_VARIABLE_PREFIX = "INTELLIJ_ORIGINAL_ENV_";

  /**
   * For each variable from {@code envs} which name matches
   * the template "{@link EnvironmentRestorer#RESERVED_ORIGINAL_VARIABLE_PREFIX}+{@code <VAR_NAME>}"
   * the next actions will be done:
   * <ul>
   *   <li>
   *     if the value of the variable "{@link EnvironmentRestorer#RESERVED_ORIGINAL_VARIABLE_PREFIX}+{@code <VAR_NAME>}" is not empty,
   *     then the value of the variable {@code <VAR_NAME>} in {@code envs} will be set
   *     to the value of the variable "{@link EnvironmentRestorer#RESERVED_ORIGINAL_VARIABLE_PREFIX}+{@code <VAR_NAME>}",
   *     otherwise the variable "{@code <VAR_NAME>}" will be removed from {@code envs}
   *   </li>
   *   <li>
   *     the variable "{@link EnvironmentRestorer#RESERVED_ORIGINAL_VARIABLE_PREFIX}+{@code <VAR_NAME>}"
   *     will be removed from the {@code envs}
   *   </li>
   * </ul>
   * This method can be useful, when the current IDE process was run with some overridden environment variables
   * and the original values of these variables were stored in corresponding created extra variables named
   * with "{@link EnvironmentRestorer#RESERVED_ORIGINAL_VARIABLE_PREFIX}+{@code <VAR_NAME>}",
   * but you need to run a new child process with original variables values,
   * because overridden variables values shouldn't be passed to child process environment.
   * So this method will restore the original variables values and remove all extra ones.
   * <p/>
   * Real case: some environment variables are overridden for IDE process in the way described above
   * by {@code plugins/remote-dev-server/build/resources/linux/scripts/launcher.sh}
   *
   * @param envs modifiable environment. The overridden variables values will be restored right in it.
   */
  public static void restoreOverriddenVars(@NotNull Map<String, String> envs) {
    List<Pair<String, String>> reserved = ContainerUtil.mapNotNull(envs.entrySet(), entry -> {
      if (entry.getKey().startsWith(RESERVED_ORIGINAL_VARIABLE_PREFIX)) {
        return new Pair<>(entry.getKey(), entry.getValue());
      }
      return null;
    });

    for (Pair<String, String> pair : reserved) {
      String originalName = pair.first.substring(RESERVED_ORIGINAL_VARIABLE_PREFIX.length());
      if (originalName.length() == 0) {
        Logger.getInstance(EnvironmentRestorer.class).warn(
          "the name of the reserved environment variable consists only of the prefix \"" +
          RESERVED_ORIGINAL_VARIABLE_PREFIX + "\". name=" + pair.first + " value=" + pair.second);
        continue;
      }

      envs.remove(pair.first);

      if (StringUtil.isNotEmpty(pair.second)) {
        envs.put(originalName, pair.second);
      }
      else {
        envs.remove(originalName);  // we assume that an empty value means no value (GTW-335)
      }
    }
  }
}
