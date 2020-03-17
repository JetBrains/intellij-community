package com.intellij.configurationScript

import com.intellij.configurationScript.providers.readComponentConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.ClassRule
import org.junit.Test

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
    assertThat(result!!.updateMethod).isEqualTo(UpdateMethod.REBASE)
  }
}

@Suppress("SameParameterValue")
private fun doReadComponentConfiguration(namePath: String, @Language("YAML") data: String): TestState? {
  return readComponentConfiguration(findValueNodeByPath(namePath, doRead(data.trimIndent().reader())!!.value)!!, TestState::class.java)
}

private class TestState : BaseState() {
  var updateMethod by enum(UpdateMethod.BRANCH_DEFAULT)
}

private enum class UpdateMethod {
  BRANCH_DEFAULT, MERGE, REBASE
}