// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics

import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Handles events from [RemoteTopic] on the frontend side.
 *
 * Register listeners via extension points in plugin.xml:
 * ```xml
 * <extensions defaultExtensionNs="com.intellij">
 *   <platform.rpc.remoteTopicListener implementation="com.example.MyListener"/>
 * </extensions>
 * ```
 *
 * Example implementation:
 * ```kotlin
 * class MyListener : RemoteTopicListener<MyEvent> {
 *   override val topic = MY_TOPIC
 *
 *   override fun handleEvent(event: MyEvent) {
 *     // Process the event
 *   }
 * }
 * ```
 *
 * @see RemoteTopic
 */
interface RemoteTopicListener<E : Any> {
  /**
   *  The [RemoteTopic] this listener subscribes to.
   */
  val topic: RemoteTopic<E>

  /**
   * Called when the [event] is received from the topic.
   */
  fun handleEvent(event: E)

  companion object {
    val EP_NAME: ExtensionPointName<RemoteTopicListener<*>> = ExtensionPointName<RemoteTopicListener<*>>("com.intellij.platform.rpc.remoteTopicListener")
  }
}