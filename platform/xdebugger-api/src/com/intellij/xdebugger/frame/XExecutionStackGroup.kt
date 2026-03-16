// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * Represents a group of [XExecutionStack]s.
 *
 * Groups can contain both individual execution stacks and nested groups for hierarchical representation.
 */
@ApiStatus.Internal
abstract class XExecutionStackGroup(val name: @NlsSafe String) : XValueContainer() {
  open val icon: Icon? = null

  abstract val stacks: List<XExecutionStack>

  abstract val groups: List<XExecutionStackGroup>
}