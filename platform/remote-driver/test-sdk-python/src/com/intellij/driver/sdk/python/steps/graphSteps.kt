// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.driver.sdk.python.steps

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.getRegistry

private const val GRAPH_ENGINE_REGISTRY_KEY = "diagrams.graph.engine.enabled"

/**
 * Returns true when the registry key of the IntelliJ graph engine is on in the IDE of [driver].
 * The engine then paints the graph views instead of yFiles, and it paints no "Powered by yFiles" notice.
 */
fun isGraphEngineEnabled(driver: Driver): Boolean = driver.getRegistry(GRAPH_ENGINE_REGISTRY_KEY).asBoolean()
