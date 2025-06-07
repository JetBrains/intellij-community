// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.commit

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.github.api.data.GHCommitCheckSuiteConclusion
import org.jetbrains.plugins.github.api.data.GHCommitCheckSuiteStatusState
import org.jetbrains.plugins.github.api.data.GHCommitStatusContextState

@GraphQLFragment("/graphql/fragment/commitStatusRollup.graphql")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "__typename", visible = true)
@JsonSubTypes(
  JsonSubTypes.Type(name = "CheckRun", value = GHCommitStatusRollupContextDTO.CheckRun::class),
  JsonSubTypes.Type(name = "StatusContext", value = GHCommitStatusRollupContextDTO.StatusContext::class)
)
sealed interface GHCommitStatusRollupContextDTO {

  data class CheckRun(val name: @NlsSafe String,
                      val title: @NlsSafe String?,
                      val checkSuite: Map<String, Map<String, Map<String, String>>>, // seems easier than 3 nested objects with a single property
                      val status: GHCommitCheckSuiteStatusState,
                      val conclusion: GHCommitCheckSuiteConclusion?,
                      val url: String) : GHCommitStatusRollupContextDTO {
    val workflowName: @NlsSafe String?
      get() = checkSuite["workflowRun"]?.get("workflow")?.get("name")
  }

  data class StatusContext(val context: @NlsSafe String,
                           val description: @NlsSafe String,
                           val state: GHCommitStatusContextState,
                           val targetUrl: String?) : GHCommitStatusRollupContextDTO

}