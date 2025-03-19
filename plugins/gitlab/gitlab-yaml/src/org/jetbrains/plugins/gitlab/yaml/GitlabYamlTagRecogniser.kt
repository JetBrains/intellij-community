// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.yaml

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.yaml.psi.YamlTagRecogniser

internal class GitlabYamlTagRecogniser: YamlTagRecogniser {
  private val knownGithubYamlTags = setOf("!reference")

  override fun isRecognizedTag(tagText: @NlsSafe String): Boolean {
    return knownGithubYamlTags.contains(tagText)
  }
}