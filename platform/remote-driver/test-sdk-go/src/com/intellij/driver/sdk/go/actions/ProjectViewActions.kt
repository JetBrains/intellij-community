package com.intellij.driver.sdk.go.actions

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolwindows.ProjectViewToolWindowUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ProjectViewActions(val driver: Driver, val ui: UiRobot) {

  fun open() {
    step("Open the Project View") {
      driver.withContext {
        ideFrame {
          leftToolWindowToolbar.projectButton.open()
          projectView().projectViewTree.waitForNodesLoaded(NODES_TIMEOUT)
        }
      }
    }
  }

  /**
   * Waits until the Project View selects a node the path of which ends with [expectedPath].
   *
   * The path of a node is read as the names of its nodes joined with a slash. A compacted directory node already
   * holds a path, so the same [expectedPath] matches both the compacted form and the expanded one.
   */
  fun assertSelectedPathEndsWith(expectedPath: String, timeout: Duration = SELECTION_TIMEOUT) {
    step("Verify the Project View selects '$expectedPath'") {
      driver.withContext {
        ideFrame {
          projectView {
            var selectedPaths = emptyList<String>()
            waitFor(
              message = "The Project View selects '$expectedPath'",
              timeout = timeout,
              errorMessage = { "The Project View selects $selectedPaths" },
            ) {
              selectedPaths = projectViewTree.collectSelectedPaths().map { it.path.joinToString("/") }
              selectedPaths.any { it.endsWith(expectedPath) }
            }
          }
        }
      }
    }
  }

  /**
   * Waits until the Project View selects the [importPath] dependency of a Go module.
   *
   * The dependencies of a Go module are the children of a node under "External Libraries". The name of a dependency
   * node holds the version next to the import path, so it is matched as a prefix.
   */
  fun assertSelectedExternalDependency(importPath: String, timeout: Duration = SELECTION_TIMEOUT) {
    step("Verify the Project View selects the '$importPath' dependency") {
      driver.withContext {
        ideFrame {
          projectView {
            var selectedPaths = emptyList<List<String>>()
            waitFor(
              message = "The Project View selects the '$importPath' dependency of '$EXTERNAL_LIBRARIES_NODE'",
              timeout = timeout,
              errorMessage = { "The Project View selects ${selectedPaths.map { it.joinToString("/") }}" },
            ) {
              selectedPaths = projectViewTree.collectSelectedPaths().map { it.path }
              selectedPaths.any { EXTERNAL_LIBRARIES_NODE in it && it.last().startsWith(importPath) }
            }
          }
        }
      }
    }
  }

  /**
   * Waits until "External Libraries" holds one Go module node for each of [expectedImportPaths], and no other one.
   *
   * The IDE gives the dependencies of every Go module a node of their own, named "Go Modules <import path>". The
   * other children of "External Libraries", the Go SDK among them, stay out of the check.
   */
  fun assertGoModuleLibraries(vararg expectedImportPaths: String, timeout: Duration = LIBRARIES_TIMEOUT) {
    val expectedNodes = expectedImportPaths.map { goModuleNode(it) }
    step("Verify the external libraries hold the Go modules ${expectedImportPaths.joinToString()}") {
      driver.withContext {
        ideFrame {
          projectView {
            var moduleNodes = emptyList<String>()
            waitFor(
              message = "'$EXTERNAL_LIBRARIES_NODE' holds ${expectedNodes.size} Go module node(s)",
              timeout = timeout,
              errorMessage = { "'$EXTERNAL_LIBRARIES_NODE' holds the Go module nodes $moduleNodes" },
            ) {
              moduleNodes = childNodesOf(EXTERNAL_LIBRARIES_NODE).filter { it.startsWith(GO_MODULE_NODE_PREFIX) }
              moduleNodes.size == expectedNodes.size &&
              expectedNodes.all { expected -> moduleNodes.any { it.startsWith(expected) } }
            }
          }
        }
      }
    }
  }

  /**
   * Waits until the node of the Go module [importPath] holds a node for every one of [expectedDependencies].
   *
   * A dependency gets a node of its own only after the IDE downloads the module into the module cache. The check
   * therefore tells that the IDE resolved the `go.mod` file of [importPath].
   */
  fun assertGoModuleDependencies(
    importPath: String,
    vararg expectedDependencies: String,
    timeout: Duration = LIBRARIES_TIMEOUT,
  ) {
    step("Verify the Go module '$importPath' holds the dependencies ${expectedDependencies.joinToString()}") {
      driver.withContext {
        ideFrame {
          projectView {
            var dependencyNodes = emptyList<String>()
            var missing = expectedDependencies.toList()
            waitFor(
              message = "The Go module '$importPath' holds ${expectedDependencies.size} dependency node(s)",
              timeout = timeout,
              errorMessage = {
                "The Go module '$importPath' has ${dependencyNodes.size} dependency node(s) and misses $missing"
              },
            ) {
              dependencyNodes = childNodesOf(EXTERNAL_LIBRARIES_NODE, goModuleNode(importPath))
              missing = expectedDependencies.filterNot { expected -> dependencyNodes.any { it.startsWith(expected) } }
              missing.isEmpty()
            }
          }
        }
      }
    }
  }

  /**
   * The names of the child nodes of the node [path] points at.
   *
   * The Project View shows no root node, so the path of a top-level node holds one element. Every name of [path] is
   * matched as a part of the name of a node. The result is empty while one node of the path is absent: the Go model
   * adds and removes the library nodes while it resolves the modules of the project.
   */
  private fun ProjectViewToolWindowUi.childNodesOf(vararg path: String): List<String> {
    projectViewTree.waitForNodesLoaded(NODES_TIMEOUT)
    try {
      // The call expands every node of the path, from the top-level one down to the node itself.
      projectViewTree.expandPath(*path, fullMatch = false)
    }
    catch (_: JTreeUiComponent.PathNotFoundException) {
      return emptyList()
    }
    return projectViewTree.collectExpandedPaths()
      .filter { row ->
        row.path.size == path.size + 1 &&
        row.path.dropLast(1).zip(path).all { (name, expected) -> name.contains(expected) }
      }
      .map { it.path.last() }
  }

  private fun goModuleNode(importPath: String) = "$GO_MODULE_NODE_PREFIX$importPath>"

  private companion object {
    /** The node of the Project View which holds the libraries of the project. */
    private const val EXTERNAL_LIBRARIES_NODE = "External Libraries"

    /** The name of the node of the dependencies of one Go module starts with this prefix. */
    private const val GO_MODULE_NODE_PREFIX = "Go Modules <"

    private val NODES_TIMEOUT = 1.minutes
    private val SELECTION_TIMEOUT = 30.seconds
    private val LIBRARIES_TIMEOUT = 2.minutes
  }
}
