// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Handles events from [ProjectRemoteTopic] on the frontend side.
 *
 * Register listeners via extension points in plugin.xml:
 * ```xml
 * <extensions defaultExtensionNs="com.intellij">
 *   <platform.rpc.projectRemoteTopicListener implementation="com.example.MyListener"/>
 * </extensions>
 * ```
 *
 * Example implementation:
 * ```kotlin
 * class MyListener : ProjectRemoteTopicListener<MyEvent> {
 *   override val topic = MY_PROJECT_TOPIC
 *
 *   override fun handleEvent(project: Project, event: MyEvent) {
 *     // Process the event for the specific project
 *   }
 * }
 * ```
 *
 * @see ProjectRemoteTopic
 */
@ApiStatus.Internal
@ApiStatus.OverrideOnly
interface ProjectRemoteTopicListener<E : Any> {
  /**
   *  The [ProjectRemoteTopic] this listener subscribes to.
   */
  val topic: ProjectRemoteTopic<E>

  /**
   * Called when the [event] is received from the topic for the specified [project].
   */
  fun handleEvent(project: Project, event: E)

  companion object {
    val EP_NAME: ExtensionPointName<ProjectRemoteTopicListener<*>> = ExtensionPointName<ProjectRemoteTopicListener<*>>("com.intellij.platform.rpc.projectRemoteTopicListener")
  }
}