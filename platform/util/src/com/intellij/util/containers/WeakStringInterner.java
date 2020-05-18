// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

/**
 * Allow to reuse equal strings to avoid memory being wasted on them. Strings are cached on weak references
 * and garbage-collected when not needed anymore.
 *
 * @see WeakInterner
 * @author peter
 * @deprecated Use {@link Interner#createWeakInterner()}
 */
@Deprecated
public class WeakStringInterner extends WeakInterner<String> {
}
