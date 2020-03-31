// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar.strategy.impl

import com.intellij.grazie.utils.Text
import org.jetbrains.annotations.ApiStatus

/**
 * Base class for replacing single chars in grammar checking strategy
 */
@Deprecated("You shouldn't replace chars, change of text may lead to unexpected result")
@ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
abstract class ReplaceCharRule {
  abstract fun replace(prefix: CharSequence, current: Char): Char
  operator fun invoke(prefix: CharSequence, current: Char) = replace(prefix, current)
}

/**
 * Rule for replacing new lines with whitespaces
 */
@Deprecated("Deprecated in base class")
@ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
object ReplaceNewLines : ReplaceCharRule() {
  override fun replace(prefix: CharSequence, current: Char) = if (Text.isNewline(current)) ' ' else current
}

/**
 * Rule for replacing slashes with whitespaces
 */
@Deprecated("Deprecated in base class")
@ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
object ReplaceSlashes : ReplaceCharRule() {
  override fun replace(prefix: CharSequence, current: Char) = if (current == '/') ' ' else current
}

/**
 * Rule for replacing asterisks with whitespaces
 */
@Deprecated("Deprecated in base class")
@ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
object ReplaceAsterisk : ReplaceCharRule() {
  override fun replace(prefix: CharSequence, current: Char) = if (current == '*') ' ' else current
}
