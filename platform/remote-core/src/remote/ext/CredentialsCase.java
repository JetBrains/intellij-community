// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote.ext;

import com.intellij.remote.CredentialsType;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public interface CredentialsCase<T> {
  CredentialsType<T> getType();

  void process(T credentials);

  static <T> CredentialsCase<T> create(@NotNull CredentialsType<T> type, @NotNull Consumer<? super T> consumer) {
    return new CredentialsCase<>() {
      @Override
      public CredentialsType<T> getType() {
        return type;
      }

      @Override
      public void process(T credentials) {
        consumer.accept(credentials);
      }
    };
  }
}
