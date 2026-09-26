package com.intellij.configurationScript

import com.intellij.openapi.components.BaseState
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.ReflectionUtil
import org.intellij.lang.annotations.Language
import org.junit.ClassRule
import org.junit.Test
import org.snakeyaml.engine.v2.nodes.NodeTuple

class ComponentStateTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @Test
  fun read() {
    val result = doReadComponentConfiguration("versionControl.git", """
    versionControl:
      git:
        updateMethod: rebase
    """)
    assertThat(result.updateMethod).isEqualTo(UpdateMethod.REBASE)
  }
}

@Suppress("SameParameterValue")
private fun doReadComponentConfiguration(namePath: String, @Language("YAML") data: String): TestState {
  return readComponentConfiguration(findValueNodeByPath(namePath, doRead(data.trimIndent().reader())!!.value)!!, TestState::class.java)
}

private fun <T : BaseState> readComponentConfiguration(nodes: List<NodeTuple>, stateClass: Class<out T>): T {
  return readIntoObject(ReflectionUtil.newInstance(stateClass), nodes)
}

private class TestState : BaseState() {
  var updateMethod by enum(UpdateMethod.BRANCH_DEFAULT)
}

@Suppress("unused")
private enum class UpdateMethod {
  BRANCH_DEFAULT, MERGE, REBASE
}