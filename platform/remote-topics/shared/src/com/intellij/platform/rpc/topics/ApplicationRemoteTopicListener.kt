// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Handles events from [ApplicationRemoteTopic] on the frontend side.
 *
 * Register listeners via extension points in plugin.xml:
 * ```xml
 * <extensions defaultExtensionNs="com.intellij">
 *   <platform.rpc.applicationRemoteTopicListener implementation="com.example.MyListener"/>
 * </extensions>
 * ```
 *
 * Example implementation:
 * ```kotlin
 * class MyListener : ApplicationRemoteTopicListener<MyEvent> {
 *   override val topic = MY_APPLICATION_TOPIC
 *
 *   override fun handleEvent(event: MyEvent) {
 *     // Process the event
 *   }
 * }
 * ```
 *
 * @see ApplicationRemoteTopic
 */
@ApiStatus.Internal
@ApiStatus.OverrideOnly
interface ApplicationRemoteTopicListener<E : Any> {
  /**
   *  The [ApplicationRemoteTopic] this listener subscribes to.
   */
  val topic: ApplicationRemoteTopic<E>

  /**
   * Called when the [event] is received from the topic.
   */
  fun handleEvent(event: E)

  companion object {
    val EP_NAME: ExtensionPointName<ApplicationRemoteTopicListener<*>> = ExtensionPointName<ApplicationRemoteTopicListener<*>>("com.intellij.platform.rpc.applicationRemoteTopicListener")
  }
}