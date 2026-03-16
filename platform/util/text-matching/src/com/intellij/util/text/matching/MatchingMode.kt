// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text.matching

enum class MatchingMode {
  /**
   * Case-insensitive matching: pattern characters match regardless of case.
   *
   * Examples:
   * - Pattern "foo" matches: "foo", "Foo", "FOO", "fOo", "FooBar"
   * - Pattern "WL" matches: "WebLogic", "Weblogic", "weblogic"
   */
  IGNORE_CASE,

  /**
   * First letter exact match: the first non-wildcard letter of the pattern
   * must have matching case with the very first letter of the candidate name.
   * Remaining pattern letters match case-insensitively.
   *
   * Wildcards (`*` and space) at the start of the pattern are skipped when
   * determining the "first letter".
   *
   * Examples:
   * - Pattern "Foo" matches: "FooBar", "Foobar" but NOT "fooBar" (case mismatch at start)
   * - Pattern "foo" matches: "fooBar", "foobar" but NOT "FooBar" (case mismatch at start)
   * - Pattern " Foo" (space wildcard) matches: "FooBar" but NOT "fooBar" ('F' vs 'f' at name start)
   * - Pattern "*foo" matches: "fooBar" but NOT "FooBar" ('f' vs 'F' at name start)
   *
   * This mode is useful for completion scenarios where the user wants to distinguish
   * between different naming conventions (e.g., "String" class vs "string" keyword).
   */
  FIRST_LETTER,

  /**
   * Fully case-sensitive: all pattern characters must match case exactly.
   *
   * Examples:
   * - Pattern "WL" matches: "WebLogic" but NOT "Weblogic", "weblogic"
   * - Pattern "foo" matches: "foo" but NOT "Foo", "FOO"
   */
  MATCH_CASE
}