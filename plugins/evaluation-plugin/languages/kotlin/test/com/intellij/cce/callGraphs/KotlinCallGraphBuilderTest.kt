package com.intellij.cce.callGraphs

import com.google.gson.Gson
import com.intellij.cce.kotlin.callGraphs.KotlinCallGraphBuilder
import com.intellij.testFramework.PlatformTestUtil.getCommunityPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import java.io.File

class KotlinCallGraphBuilderTest : BasePlatformTestCase(), ExpectedPluginModeProvider {

  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

  override fun setUp() {
    setUpWithKotlinPlugin { super.setUp() }
  }

  private data class ExpectedRaw(val nodes: List<String> = emptyList(), val edges: List<List<String>> = emptyList())

  companion object {
    fun getStaticTestDataPath(): String {
      return getCommunityPath().replace(File.separatorChar, '/') + "/plugins/evaluation-plugin/languages/kotlin/testData"
    }
  }

  override fun getTestDataPath(): String = getStaticTestDataPath()

  private fun scenarioDir(): File = File(getStaticTestDataPath(), "callGraphs/mutual_calls")

  private fun expectedCallGraph(): CallGraph {
    val text = File(scenarioDir(), "expected.json").readText()
    val raw = Gson().fromJson(text, ExpectedRaw::class.java)
    val nodes = raw.nodes.map { id ->
      CallGraphNode(
        address = CallGraphNodeLocation(projectRootFilePath = "", textRange = 0..0),
        projectName = "",
        id = id,
        qualifiedName = id
      )
    }
    val edges = raw.edges.map { pair -> CallGraphEdge(callerId = pair[0], calleeId = pair[1]) }
    return CallGraph(nodes, edges)
  }

  private fun copyScenarioSources() {
    val srcDir = File(scenarioDir(), "src")
    if (srcDir.exists()) {
      myFixture.copyDirectoryToProject("callGraphs/mutual_calls/src", "")
    }
  }

  fun testKotlinCallGraphSimpleMutualCalls() {
    copyScenarioSources()
    val expected = expectedCallGraph()

    val callGraph = KotlinCallGraphBuilder().build(myFixture.project, listOf("."))

    val actualNodeIds = callGraph.nodes.map { it.id }.toSet()
    val expectedNodeIds = expected.nodes.map { it.id }.toSet()
    assertEquals("Mismatch in node IDs for scenario 'mutual_calls'", expectedNodeIds, actualNodeIds)

    val actualEdges = callGraph.edges.map { it.callerId to it.calleeId }.toSet()
    val expectedEdges = expected.edges.map { it.callerId to it.calleeId }.toSet()
    assertEquals("Mismatch in edges for scenario 'mutual_calls'", expectedEdges, actualEdges)
  }
}
