// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.reference;

import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.function.Supplier;

/**
 * The class is necessary to debug memory allocations via soft references. All IDEA classes should use this SoftReference
 * instead of original from java.lang.ref. Whenever we suspect soft memory allocation overhead this easily becomes a hard
 * reference so we can see allocations and memory consumption in memory profiler.
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
public class SoftReference<T> extends java.lang.ref.SoftReference<T> implements Supplier<T> {
  //private final T myReferent;

  /**
   * @deprecated use {@link java.lang.ref.SoftReference#SoftReference(Object)}
   */
  @Deprecated
  public SoftReference(final T referent) {
    super(referent);
    //myReferent = referent;
  }

  //@Override
  //public T get() {
  //  return myReferent;
  //}

  public static @Nullable <T> T dereference(@Nullable Reference<T> ref) {
    return ref == null ? null : ref.get();
  }

  public static @Nullable <T> T deref(@Nullable Supplier<T> ref) {
    return ref == null ? null : ref.get();
  }
}
