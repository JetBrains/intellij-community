// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.UndoableEvaluationStep
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.util.registry.Registry

class SetupFullLineStep() : UndoableEvaluationStep {
  private var initLoggingEnabledValue: Boolean = false
  private var initLogPathValue: String? = null
  private var initLatency: Int? = null

  override val name: String = "Setup FullLine plugin step"
  override val description: String = "Enable FullLine BeamSearch logging"

  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
    initLoggingEnabledValue = java.lang.Boolean.parseBoolean(System.getProperty(LOGGING_ENABLED_PROPERTY, "false"))
    initLogPathValue = System.getProperty(LOG_PATH_PROPERTY)
    System.setProperty(LOGGING_ENABLED_PROPERTY, "true")
    val latencyRegistry = Registry.get(LATENCY_REGISTRY)
    initLatency = latencyRegistry.asInteger()
    latencyRegistry.setValue(MAX_LATENCY)
    return workspace
  }

  override fun undoStep(): UndoableEvaluationStep.UndoStep {
    return object : UndoableEvaluationStep.UndoStep {
      override val name: String = "Undo setup FullLine step"
      override val description: String = "Return default behaviour of FullLine plugin"

      override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
        System.setProperty(LOGGING_ENABLED_PROPERTY, initLoggingEnabledValue.toString())
        val logPath = initLogPathValue
        if (logPath == null) {
          System.clearProperty(LOG_PATH_PROPERTY)
        }
        else {
          System.setProperty(LOG_PATH_PROPERTY, logPath)
        }
        initLatency?.let {
          Registry.get(LATENCY_REGISTRY).setValue(it)
        }
        return workspace
      }
    }
  }

  companion object {
    private const val LOGGING_ENABLED_PROPERTY = "flcc_search_logging_enabled"
    private const val LOG_PATH_PROPERTY = "flcc_search_log_path"
    private const val LATENCY_REGISTRY = "full.line.server.host.max.latency"
    private const val MAX_LATENCY = 20000
  }
}
