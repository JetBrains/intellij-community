package com.intellij.cce.callGraphs

import com.intellij.cce.java.callGraphs.JavaCallGraphBuilder
import com.intellij.testFramework.PlatformTestUtil.getCommunityPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class JavaCallGraphBuilderTest(private val scenario: String) : BasePlatformTestCase() {

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
    return CallGraph.deserialise(text)
  }

  private fun copyScenarioSources() {
    val srcDir = File(scenarioDir(), "src")
    if (srcDir.exists()) {
      myFixture.copyDirectoryToProject("callGraphs/$scenario/src", "")
    }
  }

  private fun buildActualGraph(): CallGraph = JavaCallGraphBuilder().build(myFixture.project, listOf("."))

  private fun buildExpectedToActualIdMapByLocation(expected: CallGraph, actual: CallGraph): Map<String, String> {
    val actualByAddr: Map<CallGraphNodeLocation, CallGraphNode> = actual.nodes.associateBy { it.address }
    val mapping = mutableMapOf<String, String>()
    val usedActualIds = mutableSetOf<String>()

    for (en in expected.nodes) {
      val actualNode = actualByAddr[en.address]
      assertNotNull(
        "No actual node found by address for expected node '${en.qualifiedName}' in scenario '$scenario'. Address: ${en.address}",
        actualNode
      )
      val actualId = actualNode!!.id
      assertTrue(
        "Address mapping is not a bijection: multiple expected nodes map to the same actual node id '$actualId' in scenario '$scenario'",
        usedActualIds.add(actualId)
      )
      mapping[en.id] = actualId
    }

    assertEquals(
      "Address mapping is not a bijection by size in scenario '$scenario'",
      expected.nodes.size,
      mapping.size
    )

    return mapping
  }

  private fun remapExpectedToActual(expected: CallGraph, actual: CallGraph, idMap: Map<String, String>): CallGraph {
    val actualByAddr: Map<CallGraphNodeLocation, CallGraphNode> = actual.nodes.associateBy { it.address }

    val remappedNodes = expected.nodes.map { en ->
      val actualNode = actualByAddr.getValue(en.address)
      actualNode.copy(id = idMap.getValue(en.id))
    }

    val remappedEdges = expected.edges.map { e ->
      CallGraphEdge(
        callerId = idMap.getValue(e.callerId),
        calleeId = idMap.getValue(e.calleeId)
      )
    }

    return CallGraph(remappedNodes, remappedEdges)
  }

  private fun assertGraphsEqualAsDataClasses(expectedRemapped: CallGraph, actual: CallGraph) {
    assertEquals(
      "Mismatch in nodes for scenario '$scenario'",
      actual.nodes.toSet(),
      expectedRemapped.nodes.toSet()
    )
    assertEquals(
      "Mismatch in edges for scenario '$scenario'",
      actual.edges.toSet(),
      expectedRemapped.edges.toSet()
    )
  }

  @Test
  fun testCallGraphAgainstExpected() {
    copyScenarioSources()
    val expected = expectedCallGraph()
    val actual = buildActualGraph()

    val expectedToActualId = buildExpectedToActualIdMapByLocation(expected, actual)
    val expectedRemapped = remapExpectedToActual(expected, actual, expectedToActualId)
    assertGraphsEqualAsDataClasses(expectedRemapped, actual)
  }
}