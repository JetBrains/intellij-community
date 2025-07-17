// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.FastUtilHashingStrategies;
import it.unimi.dsi.fastutil.Hash;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Implementation of {@link Hash.Strategy} for case-insensitive String comparison, that speeds up case-insensitive hashCode
 * evaluation by accessing private String internals via VarHandle.
 * Case-insensitive hashCode contributes quite a lot in overall performance on case-insensitive platforms (Win, MacOS).
 * We don't have a luxury of predefined and cached String.hashCode for case-insensitive comparisons, hence case-insensitive
 * hash code evaluation must be as fast, as possible.
 * It is possible to speed it up a bit (~20-30%) for ASCII strings (most frequent in context of paths/file names) by accessing
 * private String internals via Unsafe/VarHandle -- this is that we're doing here.
 */
@ApiStatus.Internal
public final class OptimizedCaseInsensitiveStringHashing implements Hash.Strategy<String> {
  private static final Logger LOG = Logger.getInstance(OptimizedCaseInsensitiveStringHashing.class);

  private static final VarHandle CODER_HANDLE;
  private static final VarHandle VALUE_HANDLE;

  static {
    VarHandle coderHandle;
    VarHandle valueHandle;
    try {
      MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(String.class, MethodHandles.lookup());
      coderHandle = lookup.findVarHandle(String.class, "coder", byte.class)
        .withInvokeExactBehavior();
      valueHandle = lookup.findVarHandle(String.class, "value", byte[].class)
        .withInvokeExactBehavior();
    }
    catch (Exception e) {
      LOG.error("Can't initialize fast case-insensitive hashing strategy", e);
      coderHandle = null;
      valueHandle = null;
    }
    CODER_HANDLE = coderHandle;
    VALUE_HANDLE = valueHandle;
  }

  private static final Hash.Strategy<String> FALLBACK_STRATEGY = FastUtilHashingStrategies.getCaseInsensitiveStringStrategy();

  private static final OptimizedCaseInsensitiveStringHashing OPTIMIZED_STRATEGY = new OptimizedCaseInsensitiveStringHashing();

  public static Hash.Strategy<String> instance() {
    if (CODER_HANDLE != null && VALUE_HANDLE != null) {
      return OPTIMIZED_STRATEGY;
    }

    return FALLBACK_STRATEGY;
  }

  private OptimizedCaseInsensitiveStringHashing() { }

  @Override
  public int hashCode(@Nullable String str) {
    if (str == null) {
      return 0;
    }
    return caseInsensitiveHashCode(str);
  }

  @Override
  public boolean equals(@Nullable String a, @Nullable String b) {
    return FALLBACK_STRATEGY.equals(a, b);
  }

  @VisibleForTesting
  public static int caseInsensitiveHashCode(@NotNull String str) {
    byte coder = (byte)CODER_HANDLE.get(str);
    if (coder == 1) {// == Unicode
      return FALLBACK_STRATEGY.hashCode(str);
    }

    //(coder=0) <=> Latin1 (ASCII)
    byte[] bytes = (byte[])VALUE_HANDLE.get(str);
    int hash = 0;
    int length = str.length();
    for (int i = 0; i < length; i++) {
      byte ch = bytes[i];
      //in ASCII lower and upper case letters differ by a single bit:
      hash = 31 * hash + (ch & 0b1101_1111);
    }
    return hash;
  }

  @VisibleForTesting
  public static int caseInsensitiveHashCode(byte ch) {
    //in ASCII lower and upper case letters differ by a single bit:
    return (ch & 0b1101_1111);
  }
}
