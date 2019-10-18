// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.utils.PluginInfo;
import org.jetbrains.annotations.NotNull;

public abstract class PerformanceCareRule implements FUSRule {
     private static final int EXPECTED_TIME_MSEC = 239; // TODO:  research this constant
     private static final int MAX_ATTEMPTS = 10;

     private int failed = 0;

  @NotNull
  @Override
  public final ValidationResultType validate(@NotNull String data, @NotNull EventContext context) {
    if (failed > MAX_ATTEMPTS) return ValidationResultType.PERFORMANCE_ISSUE;
    long startedAt = System.currentTimeMillis();

    ValidationResultType resultType = doValidate(data, context);

    if (System.currentTimeMillis() - startedAt > EXPECTED_TIME_MSEC) failed++;

    return resultType;
  }

  /**
   * <p>Validates event id and event data before recording it locally. Used to ensure that no personal or proprietary data is recorded.<p/>
   *
   * <ul>
   *     <li>{@link ValidationResultType#ACCEPTED} - data is checked and should be recorded as is;</li>
   *     <li>{@link ValidationResultType#THIRD_PARTY} - data is correct but is implemented in an unknown third-party plugin, e.g. third-party file type<br/>
   *     {@link PluginInfo#isDevelopedByJetBrains()}, {@link PluginInfo#isSafeToReport()};</li>
   *     <li>{@link ValidationResultType#REJECTED} - unexpected data, e.g. cannot find run-configuration by provided id;</li>
   * </ul>
   *
   * @param data what is validated. Event id or the value of event data field.
   * @param context whole event context, i.e. both event id and event data.
   */
  @NotNull
  protected abstract ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context);
}
