// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics

import kotlinx.serialization.KSerializer
import org.jetbrains.annotations.ApiStatus

/**
 * Common interface for Remote Topics API.
 *
 * @see ProjectRemoteTopic
 * @see ApplicationRemoteTopic
 */
@ApiStatus.NonExtendable
sealed interface RemoteTopic<E : Any> {
  /**
   * Topic identifier that should be unique within the application.
   */
  val id: String

  /**
   * Serializer for topic events.
   */
  val serializer: KSerializer<E>
}