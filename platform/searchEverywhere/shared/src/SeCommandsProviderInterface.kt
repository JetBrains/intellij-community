// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface SeCommandsProviderInterface {
  fun getSupportedCommands(): List<SeCommandInfo>
}

/**
 * Rider's symbol contributor has "commands" (pattern-included search filters) arriving from the backend.
 * They may appear both in preposition and in postposition relative to the pattern.
 * This interface hacks the platform SE commands impl in a way so that Rider could have its way when it comes to SE commands
 */
@ApiStatus.Internal
interface SePossibleInternalCommandsHandling {
  fun shouldTreatAsACommandQuery(string: String): Boolean?
  fun shouldTreatAsACommandQueryWithArg(string: String): Boolean?
}