// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import java.util.function.Supplier;

/**
 * Please use {@link Supplier} instead
 */
@FunctionalInterface
public interface Getter<A> extends Supplier<A> {
}
