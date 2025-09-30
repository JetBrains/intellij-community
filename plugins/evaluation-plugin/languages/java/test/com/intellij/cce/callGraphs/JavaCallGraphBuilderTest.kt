package com.intellij.cce.callGraphs

import com.google.gson.Gson
import com.intellij.cce.java.callGraphs.JavaCallGraphBuilder
import com.intellij.testFramework.PlatformTestUtil.getCommunityPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class JavaCallGraphBuilderTest(private val scenario: String) : BasePlatformTestCase() {

  private data class ExpectedRaw(val nodes: List<String> = emptyList(), val edges: List<List<String>> = emptyList())

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Collection<Array<String>> {
      val base = File(getStaticTestDataPath(), "callGraphs")
      val scenarios = base.listFiles { f -> f.isDirectory }?.map { it.name } ?: emptyList()
      return scenarios.sorted().map { arrayOf(it) }
    }

    fun getStaticTestDataPath(): String {
      return getCommunityPath().replace(File.separatorChar, '/') + "/plugins/evaluation-plugin/languages/java/testData"
    }

  }

  override fun getTestDataPath(): String = getStaticTestDataPath()

  private fun scenarioDir(): File = File(getTestDataPath(), "callGraphs/$scenario")

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
      myFixture.copyDirectoryToProject("callGraphs/$scenario/src", "")
    }
  }

  @Test
  fun testCallGraphAgainstExpected() {
    copyScenarioSources()
    val expected = expectedCallGraph()

    val callGraph = JavaCallGraphBuilder().build(myFixture.project)

    val actualQualifiedNames = callGraph.nodes.map { it.qualifiedName }.toSet()

    val expectedQualifiedNames = expected.nodes.map { it.qualifiedName }.toSet()

    assertEquals("Mismatch in node qualifiedNames for scenario '$scenario'", expectedQualifiedNames, actualQualifiedNames)

    val idToQualifiedName = callGraph.nodes.associate { it.id to it.qualifiedName }
    val actualEdgesByQualifiedNames = callGraph.edges.map { idToQualifiedName.getValue(it.callerId) to idToQualifiedName.getValue(it.calleeId) }.toSet()

    val expectedEdgesByQualifiedNames = expected.edges.map { it.callerId to it.calleeId }.toSet()

    assertEquals("Mismatch in edges (by qualifiedName) for scenario '$scenario'", expectedEdgesByQualifiedNames, actualEdgesByQualifiedNames)
  }
}