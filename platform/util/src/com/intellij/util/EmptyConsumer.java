// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

public final class EmptyConsumer {
  public static <T> Consumer<T> getInstance() {
    //noinspection unchecked,deprecation
    return (Consumer<T>)Consumer.EMPTY_CONSUMER;
  }
}
