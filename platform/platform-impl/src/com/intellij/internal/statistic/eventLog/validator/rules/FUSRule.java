// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import org.jetbrains.annotations.NotNull;

public interface FUSRule {
    FUSRule[] EMPTY_ARRAY = new FUSRule[0];
    FUSRule TRUE = (s,c) -> ValidationResultType.ACCEPTED;
    FUSRule FALSE = (s,c) -> ValidationResultType.REJECTED;

    @NotNull
    ValidationResultType validate(@NotNull String data, @NotNull EventContext context);
}
