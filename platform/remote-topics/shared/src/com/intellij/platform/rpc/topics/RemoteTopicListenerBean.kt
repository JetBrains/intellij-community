// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics

import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * A declaration of a remote topic listener.
 *
 * The bean is read from the plugin descriptor without loading [implementation],
 * so [com.intellij.platform.rpc.topics.impl.RemoteTopicListenerIndex] finds the listeners of a topic by [topicId] and constructs a listener on the first event of its topic.
 */
abstract class RemoteTopicListenerBean<L : Any> : BaseKeyedLazyInstance<L> {
  @RequiredElement
  @Attribute("implementation")
  lateinit var implementation: String

  /** The id of the topic that the listener handles. */
  @RequiredElement
  @Attribute("topicId")
  @JvmField
  var topicId: String? = null

  @ApiStatus.Internal
  protected constructor()

  @TestOnly
  @ApiStatus.Internal
  protected constructor(listener: L, topicId: String) : super(listener) {
    implementation = listener.javaClass.name
    this.topicId = topicId
    // a programmatic registration has no declaring plugin, so the bean names the platform, as the extension point does for an object
    setPluginDescriptor(DefaultPluginDescriptor(PluginId.getId("com.intellij"), listener.javaClass.classLoader))
  }

  @ApiStatus.Internal
  override fun getImplementationClassName(): String = implementation
}

/** A declaration of [ApplicationRemoteTopicListener.EP_NAME]. */
class ApplicationRemoteTopicListenerBean : RemoteTopicListenerBean<ApplicationRemoteTopicListener<*>> {

  @Suppress("unused") // Platform creates bean via reflection.
  @ApiStatus.Internal
  constructor()

  /** Registers [listener] without a declaration. */
  @TestOnly
  @ApiStatus.Internal
  constructor(listener: ApplicationRemoteTopicListener<*>) : super(listener, listener.topic.id)
}

/** A declaration of [ProjectRemoteTopicListener.EP_NAME]. */
class ProjectRemoteTopicListenerBean : RemoteTopicListenerBean<ProjectRemoteTopicListener<*>> {

  @Suppress("unused") // Platform creates bean via reflection.
  @ApiStatus.Internal
  constructor()

  /** Registers [listener] without a declaration. */
  @TestOnly
  @ApiStatus.Internal
  constructor(listener: ProjectRemoteTopicListener<*>) : super(listener, listener.topic.id)
}
