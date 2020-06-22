// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;

/**
 * @author gregsh
 * @deprecated use {@link java.util.function.IntUnaryOperator} instead.
 */
@ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
@Deprecated
@FunctionalInterface
public interface IntIntFunction {

  IntIntFunction IDENTITY = i -> i;

  int fun(int index);
}
