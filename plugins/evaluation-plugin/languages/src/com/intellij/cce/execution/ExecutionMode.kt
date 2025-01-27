package com.intellij.cce.execution

enum class ExecutionMode(val displayName: String) {
  NONE("none"),
  LOCAL("local"),
  DOCKER("docker");

  companion object {
    fun resolve(displayName: String?): ExecutionMode {
      return values().find { it.displayName == displayName }
             ?: NONE
    }
  }
}