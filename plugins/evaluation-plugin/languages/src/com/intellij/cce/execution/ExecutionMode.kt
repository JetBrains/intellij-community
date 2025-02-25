package com.intellij.cce.execution

enum class ExecutionMode(val displayName: String) {
  LOCAL("local"),
  DOCKER("docker");

  companion object {
    fun resolve(displayName: String?): ExecutionMode {
      return entries.find { it.displayName == displayName } ?: throw IllegalArgumentException("Unknown execution mode: $displayName")
    }
  }
}