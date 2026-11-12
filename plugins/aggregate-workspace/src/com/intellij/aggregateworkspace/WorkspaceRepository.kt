package com.intellij.aggregateworkspace

data class WorkspaceRepository(
  val id: String,
  val displayName: String,
  val description: String,
) {
  val checkoutPropertyKey: String
    get() = "aggregate.workspace.checkout.$id"
}
