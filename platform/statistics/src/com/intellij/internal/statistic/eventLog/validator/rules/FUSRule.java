// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules;

import com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.utils.PluginInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Used to validate events before recording it locally.
 *
 * @see SensitiveDataValidator
 * @see CustomValidationRule
 */
public interface FUSRule {
    FUSRule[] EMPTY_ARRAY = new FUSRule[0];
    FUSRule TRUE = (s,c) -> ValidationResultType.ACCEPTED;
    FUSRule FALSE = (s,c) -> ValidationResultType.REJECTED;

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
    ValidationResultType validate(@NotNull String data, @NotNull EventContext context);

}
