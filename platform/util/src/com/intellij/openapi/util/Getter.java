// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Supplier;

/**
 * Obsolete, use {@link Supplier} instead.
 */
@ApiStatus.Obsolete
@FunctionalInterface
public interface Getter<A> extends Supplier<A> {
}
