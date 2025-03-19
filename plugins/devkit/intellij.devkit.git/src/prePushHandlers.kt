// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.commit

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.Nls

internal class KotlinPluginPrePushHandler : IssueIDPrePushHandler() {
  override val paths: List<String> = listOf("plugins/kotlin/")
  override val commitMessageRegex = Regex(".*(?:KTIJ|KT|IDEA|IJPL)-\\d+.*", RegexOption.DOT_MATCHES_ALL /* line breaks matter */)
  override val pathsToIgnore = super.pathsToIgnore.toMutableList()
    .apply { add("/fleet/plugins/kotlin/") }
    .apply { add("/plugins/kotlin/jupyter/") }

  override fun isAvailable(): Boolean = Registry.`is`("kotlin.commit.message.validation.enabled", true)
  override fun getPresentableName(): String = DevKitGitBundle.message("push.commit.handler.name")
}

internal class IntelliJPrePushHandler : IssueIDPrePushHandler() {
  override val paths: List<String> = listOf("community", "platform")
  override val pathsToIgnore: List<String> = listOf("plugins/kotlin/")
  override val commitMessageRegex = Regex(".*[A-Z]+-\\d+.*", RegexOption.DOT_MATCHES_ALL)
  override val ignorePattern = Regex("(tests|cleanup):.*")

  override fun isAvailable(): Boolean = Registry.`is`("intellij.commit.message.validation.enabled", true)
  override fun getPresentableName(): @Nls String = DevKitGitBundle.message("push.commit.handler.idea.name")
}