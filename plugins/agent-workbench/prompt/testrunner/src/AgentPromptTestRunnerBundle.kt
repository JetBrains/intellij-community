// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.testrunner

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

const val AGENT_PROMPT_TEST_RUNNER_BUNDLE: @NonNls String = "messages.AgentPromptTestRunnerBundle"

internal object AgentPromptTestRunnerBundle {
  private val BUNDLE = DynamicBundle(AgentPromptTestRunnerBundle::class.java, AGENT_PROMPT_TEST_RUNNER_BUNDLE)

  fun message(key: @PropertyKey(resourceBundle = AGENT_PROMPT_TEST_RUNNER_BUNDLE) String, vararg params: Any): @Nls String {
    return BUNDLE.getMessage(key, *params)
  }
}

