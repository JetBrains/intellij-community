// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ref.DebugReflectionUtil
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.Field
import java.util.function.Predicate

@TestApplication
class DebugReflectionUtilTest {

  @Test
  fun `isLoaded returns false for not loaded class`() {
    assertFalse {
      DebugReflectionUtil.isLoaded(Thread.currentThread().contextClassLoader, "com.intellij.util.ThisClassWillFailOnLoad")
    }
    assertThrows<ExceptionInInitializerError> {
      ThisClassWillFailOnLoad()
    }
  }


  @Test
  fun `isLoaded returns false for not loaded class 2`() {
    assertFalse {
      DebugReflectionUtil.isLoaded(Thread.currentThread().contextClassLoader, "com.intellij.util.ThisClassWillNotFailOnLoad")
    }
    ThisClassWillNotFailOnLoad()
    assertTrue {
      DebugReflectionUtil.isLoaded(Thread.currentThread().contextClassLoader, "com.intellij.util.ThisClassWillNotFailOnLoad")
    }
  }

  @Test
  fun `walkObjects skips excluded instance field`() {
    val leaked = LeakTarget()

    assertFalse {
      walkObjects(InstanceLeakHolder(leaked))
    }
    assertTrue {
      walkObjects(InstanceLeakHolder(leaked), Predicate { field -> field.name != "ignored" })
    }
  }

  @Test
  fun `walkObjects still traverses allowed sibling field`() {
    val leaked = LeakTarget()

    assertFalse {
      walkObjects(SiblingLeakHolder(IrrelevantValue(), leaked), Predicate { field -> field.name != "ignored" })
    }
  }

  @Test
  fun `walkObjects skips excluded static field`() {
    StaticLeakHolder.leaked

    assertFalse {
      walkObjects(StaticLeakHolder::class.java)
    }
    assertTrue {
      walkObjects(StaticLeakHolder::class.java, Predicate { field -> field.name != "leaked" })
    }
  }

  private fun walkObjects(root: Any, shouldExamineField: Predicate<Field> = Predicate { true }): Boolean {
    return DebugReflectionUtil.walkObjects(
      5,
      mapOf(root to "root"),
      LeakTarget::class.java,
      Predicate { true },
      shouldExamineField,
      PairProcessor { _, _ -> false },
    )
  }
}

private class LeakTarget

@Suppress("unused")
private class InstanceLeakHolder(val ignored: LeakTarget)

@Suppress("unused")
private class SiblingLeakHolder(val ignored: IrrelevantValue, val followed: LeakTarget)

private class IrrelevantValue

private class StaticLeakHolder {
  companion object {
    @JvmField
    val leaked = LeakTarget()
  }
}
