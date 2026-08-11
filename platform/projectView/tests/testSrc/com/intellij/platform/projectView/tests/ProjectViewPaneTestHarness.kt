// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.frontend.impl.FrontendProjectViewPaneTreeModel
import com.intellij.platform.projectView.frontend.impl.Node
import com.intellij.platform.projectView.pane.FrontendProjectViewPaneAggregator
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Drives the real Project View pipeline for a single pane, almost end-to-end, and hands a
 * [ProjectViewPaneTester] to [block]:
 *
 * ```
 * files/PSI → BackendProjectViewPaneService → ProjectViewRpcImpl.getPaneStateFlow (event.toDTO())
 *   → [in-process RemoteApiProviderService, NO serialization]  (see AbstractProjectViewPaneTest)
 *   → FrontendProjectViewPaneAggregator (dto.toEvent(), unchanged)
 *   → FrontendProjectViewPaneTreeModel.applyStateChange → treeModel
 * ```
 *
 * It deliberately stops short of `TreeBasedFrontendProjectViewPane` (the Swing pane): it constructs a
 * [FrontendProjectViewPaneTreeModel] directly and replicates the two pump loops that
 * `ProjectViewToolWindowServiceImpl.managePane` runs (state events in, requests out), minus Swing.
 *
 * All access to the tree model is confined to [Dispatchers.EDT] (production confines it to
 * `Dispatchers.UI`) because the model's node index is not thread-safe; suspending inside the tester
 * releases the EDT so the collector coroutine can apply the next event.
 *
 * The caller is responsible for having installed the in-process RPC resolver first (extend
 * [AbstractProjectViewPaneTest]) and for running inside a bounded `timeoutRunBlocking { }`.
 */
internal suspend fun <T> withProjectViewPane(
  project: Project,
  paneId: ProjectViewPaneId,
  block: suspend (ProjectViewPaneTester) -> T,
): T = coroutineScope {
  val aggregator = FrontendProjectViewPaneAggregator.getInstance(project)
  val descriptors = aggregator.getPaneDescriptorsFlow().first { descriptors ->
    descriptors.any { it.id == paneId }
  }
  val descriptor = descriptors.first { it.id == paneId }
  val model = FrontendProjectViewPaneTreeModel(project, descriptor)

  // Harness-owned progress signal, bumped once per applied event, so the tester can await population.
  val applied = MutableStateFlow(0L)

  val pumps = childScope("ProjectViewPaneTestHarness pumps")
  // Consumer loop (EDT-confined): apply backend events into the tree model.
  pumps.launch(Dispatchers.EDT) {
    aggregator.getPaneStateFlow(descriptor).collect { event ->
      model.applyStateChange(event)
      applied.update { it + 1 }
    }
  }
  // Request loop (off-EDT): forward outbound requests (load-children/navigate/...) to the backend.
  pumps.launch {
    val out = aggregator.getPaneRequestChannel(descriptor)
    for (request in model.requestChannel) {
      out.send(request)
    }
  }

  try {
    withContext(Dispatchers.EDT) {
      block(ProjectViewPaneTester(model, applied))
    }
  }
  finally {
    pumps.cancel()
  }
}

/**
 * Test-facing view over a live [FrontendProjectViewPaneTreeModel]. Every method must be called on
 * [Dispatchers.EDT] — [withProjectViewPane] already runs the `block` there.
 */
internal class ProjectViewPaneTester internal constructor(
  private val model: FrontendProjectViewPaneTreeModel,
  private val applied: MutableStateFlow<Long>,
) {
  /** Suspends until the (single) real root node exists. */
  suspend fun awaitRoot(): Node {
    var seen = applied.value
    while (true) {
      (model.treeModel.root as? Node)?.let { return it }
      seen = applied.first { it > seen }
    }
  }

  /**
   * Descends from the root by [pathByMainText] (the first element must match the root's text),
   * loading each level lazily. Returns the addressed node.
   */
  suspend fun expand(vararg pathByMainText: String): Node {
    require(pathByMainText.isNotEmpty()) { "The path must not be empty" }
    val root = awaitRoot()
    require(root.presentation.mainText == pathByMainText.first()) {
      "The root is '${root.presentation.mainText}', but the path starts with '${pathByMainText.first()}'"
    }
    var node = root
    for (name in pathByMainText.drop(1)) {
      val children = childrenOf(node) ?: error("'${node.presentation.mainText}' has no children (looking for '$name')")
      node = children.firstOrNull { it.presentation.mainText == name }
             ?: error("'$name' not found under '${node.presentation.mainText}'. Children: ${children.map { it.presentation.mainText }}")
    }
    return node
  }

  /** Force-expands every non-leaf node and returns an indented dump (one leading space per depth). */
  suspend fun dumpTree(maxDepth: Int = Int.MAX_VALUE): String {
    val sb = StringBuilder()
    dump(awaitRoot(), 0, maxDepth, sb)
    return sb.toString().trimEnd()
  }

  /** Like [dumpTree], but rooted at the node addressed by [pathByMainText]. */
  suspend fun dumpSubtree(vararg pathByMainText: String): String {
    val sb = StringBuilder()
    dump(expand(*pathByMainText), 0, Int.MAX_VALUE, sb)
    return sb.toString().trimEnd()
  }

  suspend fun assertTree(expected: String, maxDepth: Int = Int.MAX_VALUE) {
    Assertions.assertEquals(expected.trimIndent().trimEnd(), dumpTree(maxDepth))
  }

  suspend fun assertSubtree(pathByMainText: List<String>, expected: String) {
    Assertions.assertEquals(expected.trimIndent().trimEnd(), dumpSubtree(*pathByMainText.toTypedArray()))
  }

  /**
   * Sends the cut/copy/paste/delete requests the frontend provider sends, carrying the node IDs of the
   * given nodes as the current selection. Fire-and-forget, exactly like in production: await the observable
   * effect (the clipboard, the tree, the VFS) rather than the call itself.
   */
  fun requestCopy(vararg nodes: Node): Unit = model.requestCopy(nodes.map { it.projectViewNode.id })

  fun requestCut(vararg nodes: Node): Unit = model.requestCut(nodes.map { it.projectViewNode.id })

  fun requestPaste(vararg nodes: Node): Unit = model.requestPaste(nodes.map { it.projectViewNode.id })

  fun requestDelete(vararg nodes: Node): Unit = model.requestDelete(nodes.map { it.projectViewNode.id })

  private suspend fun childrenOf(node: Node): List<Node>? = model.awaitNodeChildren(node) { true }

  private suspend fun dump(node: Node, depth: Int, maxDepth: Int, sb: StringBuilder) {
    sb.append(" ".repeat(depth)).append(node.presentation.mainText).append('\n')
    if (node.presentation.isLeaf || depth >= maxDepth) return
    val children = childrenOf(node) ?: return
    for (child in children) {
      dump(child, depth + 1, maxDepth, sb)
    }
  }
}

/**
 * Resolves a repo-relative [relativePath] (e.g. `platform/projectView/tests/testData/foo`) to an
 * absolute path, trying the ultimate root first and then the `community/` sub-root — the standard
 * JUnit5 test-resource lookup (see `JUnit5ProjectFixtureTest`).
 */
internal fun projectViewTestDataPath(testClass: Class<*>, relativePath: String): Path {
  val home = PathManager.getHomeDirFor(testClass) ?: error("Cannot determine the home dir for $testClass")
  val direct = home.resolve(relativePath)
  return if (direct.exists()) direct else home.resolve("community/$relativePath")
}
