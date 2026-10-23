// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.api

/**
 * Selects the API that a test uses to run code on an [EelHolder]: the eel or the legacy target ([EelHolder.target]).
 *
 * Use it as a test parameter to run the same test through each API:
 * ```kotlin
 * @CartesianTest
 * fun testExec(@EelSource eelHolder: EelHolder, @CartesianTest.Enum eelOrTarget: EelOrTarget) {
 *   val binary = when (eelOrTarget) {
 *     EelOrTarget.EEL -> BinOnEel(shell)
 *     EelOrTarget.TARGET -> BinOnTarget(shell.toString(), eelHolder.target)
 *   }
 * }
 * ```
 * With `@ParameterizedTest`, use `@EnumSource(EelOrTarget::class)`.
 *
 * Do not declare a test-local enum or a `Boolean` for this choice.
 */
enum class EelOrTarget {
  /** The code runs through the eel API ([EelHolder.eel]). */
  EEL,

  /** The code runs through the legacy targets API ([EelHolder.target]). */
  TARGET,
}
