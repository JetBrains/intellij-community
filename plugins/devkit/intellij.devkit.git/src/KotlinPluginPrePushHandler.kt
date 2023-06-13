// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry

class KotlinPluginPrePushHandler(project: Project) : IssueIDPrePushHandler(project) {
  override val paths: List<String> = listOf("plugins/kotlin/")
  override val commitMessageRegex = Regex(".*KTIJ-\\d+.*", RegexOption.DOT_MATCHES_ALL /* line breaks matter */)
  override val pathsToIgnore = super.pathsToIgnore.toMutableList()
    .apply { add("/fleet/plugins/kotlin/") }
    .apply { add("/plugins/kotlin/jupyter/") }

  override fun isAvailable() = Registry.`is`("kotlin.commit.message.validation.enabled", true)
  override fun getPresentableName(): String = DevKitGitBundle.message("push.commit.handler.name")
}

class IntelliJPrePushHandler(project: Project) : IssueIDPrePushHandler(project) {
  override val paths = listOf("community", "platform")
  override val commitMessageRegex = Regex(".*\\w{2,}-\\d+.*", RegexOption.DOT_MATCHES_ALL)
  override fun isAvailable() = Registry.`is`("intellij.commit.message.validation.enabled", true)
  override fun getPresentableName() = DevKitGitBundle.message("push.commit.handler.idea.name")
}