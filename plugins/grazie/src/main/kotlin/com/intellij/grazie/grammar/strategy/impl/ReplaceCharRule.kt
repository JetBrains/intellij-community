// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar.strategy.impl

import org.jetbrains.annotations.ApiStatus

/**
 * Base class for replacing single chars in grammar checking strategy
 */
@Deprecated("You shouldn't replace chars, change of text may lead to unexpected result")
@ApiStatus.ScheduledForRemoval
abstract class ReplaceCharRule {
  abstract fun replace(prefix: CharSequence, current: Char): Char
  operator fun invoke(prefix: CharSequence, current: Char) = replace(prefix, current)
}
