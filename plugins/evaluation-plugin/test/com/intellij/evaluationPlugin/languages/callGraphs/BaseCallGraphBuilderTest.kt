package com.intellij.evaluationPlugin.languages.callGraphs

import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import java.io.File

object CallGraphTestUtil {
  private fun expectedCallGraph(testFixture: CodeInsightTestFixture, testCaseDataDirectory: String): CallGraph {
    val expectedJsonFile = File(testFixture.testDataPath, "$testCaseDataDirectory/expected.json")
    val text = expectedJsonFile.readText()
    return CallGraph.deserialise(text)
  }

  private fun copyScenarioSources(testFixture: CodeInsightTestFixture, testCaseDataDirectory: String) {
    val srcDir = File("$testCaseDataDirectory/src")
    testFixture.copyDirectoryToProject(srcDir.path, "")
  }

  private fun buildActualGraph(
    callGraphBuilder: CallGraphBuilder, project: Project,
  ): CallGraph = callGraphBuilder.build(project, listOf("."))

  private fun buildExpectedToActualIdMapByLocation(expected: CallGraph, actual: CallGraph, scenarioName: String): Map<String, String> {
    val actualByAddr: Map<CallGraphNodeLocation, CallGraphNode> = actual.nodes.associateBy { it.address }
    val mapping = mutableMapOf<String, String>()
    val usedActualIds = mutableSetOf<String>()

    for (en in expected.nodes) {
      val actualNode = actualByAddr[en.address]
      assertNotNull(
        "No actual node found by address for expected node '${en.qualifiedName}' in scenario '$scenarioName'. Address: ${en.address}",
        actualNode
      )
      val actualId = actualNode!!.id
      assertTrue(
        "Address mapping is not a bijection: multiple expected nodes map to the same actual node id '$actualId' in scenario '$scenarioName'",
        usedActualIds.add(actualId)
      )
      mapping[en.id] = actualId
    }

    assertEquals(
      "Address mapping is not a bijection by size in scenario '$scenarioName'",
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

  private fun assertGraphsEqualAsDataClasses(expectedRemapped: CallGraph, actual: CallGraph, scenarioName: String) {
    assertEquals(
      "Mismatch in nodes for scenario '$scenarioName'",
      actual.nodes.toSet(),
      expectedRemapped.nodes.toSet()
    )
    assertEquals(
      "Mismatch in edges for scenario '$scenarioName'",
      actual.edges.toSet(),
      expectedRemapped.edges.toSet()
    )
  }

  fun doTestGeneratedGraphEqualsExpected(
    scenarioName: String,
    testCaseDataDirectory: String,
    testFixture: CodeInsightTestFixture,
    callGraphBuilder: CallGraphBuilder,
  ) {
    copyScenarioSources(testFixture, testCaseDataDirectory)
    val expected = expectedCallGraph(testFixture, testCaseDataDirectory)
    val actual = buildActualGraph(callGraphBuilder, testFixture.project)

    val expectedToActualId = buildExpectedToActualIdMapByLocation(expected, actual, scenarioName)
    val expectedRemapped = remapExpectedToActual(expected, actual, expectedToActualId)
    assertGraphsEqualAsDataClasses(expectedRemapped, actual, scenarioName)
  }
}
