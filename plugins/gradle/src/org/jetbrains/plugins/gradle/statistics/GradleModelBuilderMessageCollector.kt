// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.statistics

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.tooling.Message

object GradleModelBuilderMessageCollector : CounterUsagesCollector() {

  override fun getGroup() = GROUP

  private val GROUP: EventLogGroup = EventLogGroup("build.gradle.errors", 19)

  private val ACTIVITY_ID = EventFields.Long("ide_activity_id")
  private val MESSAGE_KIND = EventFields.Enum<Message.Kind>("message_kind")
  private val MESSAGE_GROUP = EventFields.String("message_group", listOf(
    Messages.PROJECT_MODEL_GROUP,

    Messages.SCALA_PROJECT_MODEL_GROUP,

    Messages.TASK_WARM_UP_GROUP,
    Messages.TASK_MODEL_GROUP,

    Messages.SOURCE_SET_MODEL_GROUP,
    Messages.SOURCE_SET_MODEL_PROJECT_TASK_ARTIFACT_GROUP,
    Messages.SOURCE_SET_MODEL_SKIPPED_PROJECT_TASK_ARTIFACT_GROUP,
    Messages.SOURCE_SET_MODEL_NON_SOURCE_SET_ARTIFACT_GROUP,
    Messages.SOURCE_SET_MODEL_SKIPPED_NON_SOURCE_SET_ARTIFACT_GROUP,
    Messages.SOURCE_SET_MODEL_PROJECT_CONFIGURATION_ARTIFACT_GROUP,
    Messages.SOURCE_SET_MODEL_SKIPPED_PROJECT_CONFIGURATION_ARTIFACT_GROUP,
    Messages.SOURCE_SET_MODEL_SOURCE_SET_ARTIFACT_GROUP,
    Messages.SOURCE_SET_MODEL_SKIPPED_SOURCE_SET_ARTIFACT_GROUP,

    Messages.RESOURCE_FILTER_MODEL_GROUP,

    Messages.DEPENDENCY_DOWNLOAD_POLICY_MODEL_GROUP,
    Messages.DEPENDENCY_DOWNLOAD_POLICY_MODEL_CACHE_GET_GROUP,
    Messages.DEPENDENCY_DOWNLOAD_POLICY_MODEL_CACHE_SET_GROUP,

    Messages.SOURCE_SET_ARTIFACT_INDEX_GROUP,
    Messages.SOURCE_SET_ARTIFACT_INDEX_CACHE_SET_GROUP,

    Messages.SOURCE_SET_DEPENDENCY_MODEL_GROUP,

    Messages.EAR_CONFIGURATION_MODEL_GROUP,
    Messages.WAR_CONFIGURATION_MODEL_GROUP,

    Messages.DEPENDENCY_CLASSPATH_MODEL_GROUP,
    Messages.DEPENDENCY_ACCESSOR_MODEL_GROUP,
    Messages.DEPENDENCY_GRAPH_MODEL_GROUP,

    Messages.INTELLIJ_SETTINGS_MODEL_GROUP,
    Messages.INTELLIJ_PROJECT_SETTINGS_MODEL_GROUP,

    Messages.BUILDSCRIPT_CLASSPATH_MODEL_GROUP,
    Messages.BUILDSCRIPT_CLASSPATH_MODEL_CACHE_GET_GROUP,
    Messages.BUILDSCRIPT_CLASSPATH_MODEL_CACHE_SET_GROUP,

    Messages.TEST_MODEL_GROUP,
    Messages.MAVEN_REPOSITORY_MODEL_GROUP,
    Messages.ANNOTATION_PROCESSOR_MODEL_GROUP,
    Messages.PROJECT_EXTENSION_MODEL_GROUP,
    Messages.VERSION_CATALOG_MODEL_GROUP,
  ))

  private val MODEL_BUILDER_MESSAGE_RECEIVED_EVENT = GROUP.registerEvent("model.builder.message.received", ACTIVITY_ID, MESSAGE_KIND, MESSAGE_GROUP)

  @JvmStatic
  fun logModelBuilderMessage(project: Project?, activityId: Long, message: Message) {
    MODEL_BUILDER_MESSAGE_RECEIVED_EVENT.log(project, activityId, message.kind, message.group)
  }
}