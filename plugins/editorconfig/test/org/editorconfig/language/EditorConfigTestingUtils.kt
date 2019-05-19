// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language

import junit.framework.TestCase.assertTrue

internal fun <T> assertIterableEquals(first: Iterable<T>, second: Iterable<T>) {
  assertTrue(first.all(second::contains))
  assertTrue(second.all(first::contains))
}
