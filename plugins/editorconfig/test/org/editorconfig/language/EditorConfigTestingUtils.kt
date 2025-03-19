// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language

import org.junit.Assert

internal fun <T> assertIterableEquals(first: Iterable<T>, second: Iterable<T>) {
  Assert.assertEquals(first.toSet(), second.toSet())
}
