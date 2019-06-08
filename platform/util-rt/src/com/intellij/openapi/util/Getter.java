// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

/**
 * Please use {@link java.util.function.Supplier} instead
 */
public interface Getter<A> {
  A get();
}
