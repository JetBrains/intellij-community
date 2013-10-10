/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.reference;

import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;

/**
 * The class is necessary to debug memory allocations via soft references. All IDEA classes should use this SoftReference
 * instead of original from java.lang.ref. Whenever we suspect soft memory allocation overhead this easily becomes a hard
 * reference so we can see allocations and memory consumption in memory profiler.
 *
 * @author max
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
public class SoftReference<T> extends java.lang.ref.SoftReference<T> {
  //private final T myReferent;

  public SoftReference(final T referent) {
    super(referent);
    //myReferent = referent;
  }

  public SoftReference(final T referent, final ReferenceQueue<? super T> q) {
    super(referent, q);
    //myReferent = referent;
  }

  //@Override
  //public T get() {
  //  return myReferent;
  //}

  @Nullable
  public static <T> T dereference(@Nullable Reference<T> ref) {
    return ref == null ? null : ref.get();
  }
}
