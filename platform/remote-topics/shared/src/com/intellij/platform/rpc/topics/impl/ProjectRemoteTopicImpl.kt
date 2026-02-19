// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics.impl

import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import kotlinx.serialization.KSerializer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class ProjectRemoteTopicImpl<E : Any>(
  override val id: String,
  override val serializer: KSerializer<E>,
) : ProjectRemoteTopic<E> {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ProjectRemoteTopicImpl<*>) return false

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }

  override fun toString(): String {
    return "ProjectRemoteTopicImpl(id='$id', serializer=$serializer)"
  }
}