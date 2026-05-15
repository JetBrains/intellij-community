// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.vcs.context

import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.IssueNavigationConfiguration

internal object AgentPromptVcsIssueUrls {
  internal fun resolveIssueUrls(project: Project, text: String): List<String> {
    if (text.isBlank()) {
      return emptyList()
    }

    val issueUrls = LinkedHashSet<String>()
    IssueNavigationConfiguration.getInstance(project)
      .findIssueLinks(text)
      .asSequence()
      .filter { match -> match.isIssueMatch }
      .map { match -> match.targetUrl.trim() }
      .filter(String::isNotEmpty)
      .forEach(issueUrls::add)
    return issueUrls.toList()
  }

  internal fun buildVcsCommitPayloadEntry(
    hash: String,
    rootPath: String?,
    issueUrls: List<String>,
    subject: String? = null,
    author: String? = null,
    commitTimeMs: Long? = null,
    rootName: String? = null,
  ): AgentPromptPayloadValue.Obj {
    val fields = linkedMapOf<String, AgentPromptPayloadValue>(
      "hash" to AgentPromptPayload.str(hash),
    )
    rootPath?.let { path ->
      fields["rootPath"] = AgentPromptPayload.str(path)
    }
    rootName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
      fields["rootName"] = AgentPromptPayload.str(name)
    }
    subject?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
      fields["subject"] = AgentPromptPayload.str(text)
    }
    author?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
      fields["author"] = AgentPromptPayload.str(name)
    }
    commitTimeMs?.takeIf { it > 0L }?.let { timestamp ->
      fields["commitTimeMs"] = AgentPromptPayloadValue.Num(timestamp.toString())
    }
    if (issueUrls.isNotEmpty()) {
      fields["issueUrls"] = AgentPromptPayloadValue.Arr(issueUrls.map(AgentPromptPayload::str))
    }
    return AgentPromptPayloadValue.Obj(fields)
  }
}
