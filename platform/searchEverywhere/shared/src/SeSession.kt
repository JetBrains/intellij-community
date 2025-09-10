// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import fleet.kernel.DurableRef
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Serializable
sealed interface SeSession

@ApiStatus.Internal
@Serializable
class SeSessionImpl(val ref: DurableRef<SeSessionEntity>): SeSession

@ApiStatus.Internal
fun SeSession.asRef(): DurableRef<SeSessionEntity> = (this as SeSessionImpl).ref