package com.intellij.aggregateworkspace

object WorkspaceRepositoryRegistry {
  val repositories: List<WorkspaceRepository> = listOf(
    WorkspaceRepository(
      id = "Jupyterlab",
      displayName = "Jupyterlab",
      description = "Notebook workspace",
    ),
    WorkspaceRepository(
      id = "notebook",
      displayName = "notebook",
      description = "Jupyter interactive notebook",
    ),
    WorkspaceRepository(
      id = "HUNT",
      displayName = "HUNT",
      description = "Python + Kotlin research workspace",
    ),
    WorkspaceRepository(
      id = "burpsuite",
      displayName = "burpsuite",
      description = "Security tooling suite",
    ),
    WorkspaceRepository(
      id = "BurpKit",
      displayName = "BurpKit",
      description = "Burp suite plugin tooling",
    ),
  )
}
