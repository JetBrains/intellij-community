package com.intellij.cce.evaluable.step

import com.intellij.cce.evaluation.step.SetupRegistryStep
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.withValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.Mockito.mock

class SetupRegistryStepTest {
  private val workspace = mock(EvaluationWorkspace::class.java)

  @Test
  fun `test setting registry`() {
    val value = true
    Registry.get(BOOLEAN_KEY_1).withValue(false) {
      SetupRegistryStep("$BOOLEAN_KEY_1=$value").start(workspace)
      assert(Registry.get(BOOLEAN_KEY_1).asBoolean() == value)
    }
  }

  @Test
  fun `test setting multiple registry`() {
    val value1 = true
    val value2 = false
    Registry.get(BOOLEAN_KEY_1).withValue(!value1) {
      Registry.get(BOOLEAN_KEY_2).withValue(!value2) {
        SetupRegistryStep("$BOOLEAN_KEY_1=$value1,$BOOLEAN_KEY_2=$value2").start(workspace)
        assert(Registry.get(BOOLEAN_KEY_1).asBoolean() == value1)
        assert(Registry.get(BOOLEAN_KEY_2).asBoolean() == value2)
      }
    }
  }

  @Test
  fun `test empty registry`() {
    assertDoesNotThrow {
      SetupRegistryStep("").start(workspace)
    }
  }
}

private const val BOOLEAN_KEY_1 = "ide.tree.experimental.layout.cache"
private const val BOOLEAN_KEY_2 = "ide.tree.experimental.layout.cache.debug"
