// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated Use {@link Interner#createStringInterner()}.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval
public class StringInterner extends HashSetInterner<String> {
}
