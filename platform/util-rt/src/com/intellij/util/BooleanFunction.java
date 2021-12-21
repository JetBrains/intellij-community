// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

/**
 * @deprecated Use {@link java.util.function.Predicate} instead
 */
@Deprecated
public interface BooleanFunction<S> {
    boolean fun(S s);
}