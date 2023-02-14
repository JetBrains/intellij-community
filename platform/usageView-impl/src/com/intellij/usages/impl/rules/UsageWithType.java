// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.usages.Usage;
import org.jetbrains.annotations.Nullable;

public interface UsageWithType extends Usage {

  @Nullable UsageType getUsageType();
}
