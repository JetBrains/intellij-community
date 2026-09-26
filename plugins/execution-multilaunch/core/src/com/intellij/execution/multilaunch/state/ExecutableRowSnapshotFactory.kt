package com.intellij.execution.multilaunch.state

import com.intellij.execution.multilaunch.design.ExecutableRow
import com.intellij.execution.multilaunch.execution.conditions.Condition
import com.intellij.execution.multilaunch.execution.executables.Executable

object ExecutableRowSnapshotFactory {
  fun create(descriptor: ExecutableRow): ExecutableRowSnapshot {
    return create(
      descriptor.executable,
      descriptor.condition,
      descriptor.disableDebugging
    )
  }

  fun create(executable: Executable?, condition: Condition?, disableDebugging: Boolean): ExecutableRowSnapshot {
    val executableSnapshot = ExecutableSnapshot().apply {
      id = executable?.let { createCompositeId(it.template.type, it.uniqueId) }
      executable?.saveAttributes(this)
    }
    val conditionSnapshot = ConditionSnapshot().apply {
      type = condition?.template?.type
      condition?.saveAttributes(this)
    }

    return ExecutableRowSnapshot().apply {
      this.executable = executableSnapshot
      this.condition = conditionSnapshot
      this.disableDebugging = disableDebugging
    }
  }

  fun createCompositeId(type: String, uniqueId: String) = "$type:$uniqueId"
}

internal fun ExecutableRow.toSnapshot(): ExecutableRowSnapshot =
  ExecutableRowSnapshotFactory.create(this)