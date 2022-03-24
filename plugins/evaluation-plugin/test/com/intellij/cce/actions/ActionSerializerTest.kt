package com.intellij.cce.actions

import com.intellij.cce.core.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActionSerializerTest {
  @Test
  fun `empty actions`() {
    doTest(withActions(0, emptyList()))
  }

  @Test
  fun `with properties`() {
    val properties = SimpleTokenProperties.create(TypeProperty.METHOD_CALL, SymbolLocation.UNKNOWN) {}
    doTest(withActions(1, listOf<Action>(CallCompletion("f", "foo", properties))))
  }

  @Test
  fun `with java properties`() {
    val properties = JvmProperties.create(TypeProperty.METHOD_CALL, SymbolLocation.PROJECT) {
      isStatic = true
      packageName = "java.util"
    }
    val after = doTest(withActions(1, listOf<Action>(CallCompletion("f", "foo", properties))))
    val javaProperties = PropertyAdapters.Jvm.adapt((after.actions[0] as CallCompletion).nodeProperties)
    assertTrue(javaProperties?.isStatic!!)
    assertEquals("java.util", javaProperties.packageName)
  }

  @Test
  fun `with all actions`() {
    val props = SimpleTokenProperties.create(TypeProperty.METHOD_CALL, SymbolLocation.UNKNOWN) {}
    doTest(withActions(1, listOf(MoveCaret(10), DeleteRange(10, 20), PrintText("Hello"), callCompletion(props), FinishSession())))
  }

  @Test
  fun `properties with custom value`() {
    val props = SimpleTokenProperties.create(TypeProperty.METHOD_CALL, SymbolLocation.UNKNOWN) {
      put("custom", "42")
    }
    val action = doTest(withActions(1, listOf(callCompletion(props)))).actions[0]
    assert((action as CallCompletion).nodeProperties.additionalProperty("custom") == "42")
  }

  private fun callCompletion(properties: TokenProperties): CallCompletion {
    return CallCompletion("f", "foo", properties)
  }

  private fun withActions(sessionsCount: Int, actions: List<Action>): FileActions {
    return FileActions("foo/bar", "42", sessionsCount, actions)
  }

  private fun doTest(before: FileActions): FileActions {
    val after = ActionSerializer.deserialize(ActionSerializer.serialize(before))
    assertEquals(before.path, after.path)
    assertEquals(before.sessionsCount, after.sessionsCount)
    assertEquals(before.checksum, after.checksum)
    assertActionsEquals(before.actions, after.actions)
    return after
  }

  private fun assertActionsEquals(before: List<Action>, after: List<Action>) {
    assertEquals(before.size, after.size)
    for ((actionBefore, actionAfter) in before.zip(after)) {
      assertEquals(actionBefore.type, actionAfter.type)
      if (actionBefore is CallCompletion) {
        require(actionAfter is CallCompletion)
        assertEquals(actionBefore.expectedText, actionAfter.expectedText)
        assertEquals(actionBefore.prefix, actionAfter.prefix)
        assertEquals(actionBefore.nodeProperties.describe(), actionAfter.nodeProperties.describe())
      }
      else if (actionAfter.type != Action.ActionType.FINISH_SESSION) {
        assertEquals(actionBefore, actionAfter)
      }
    }
  }
}