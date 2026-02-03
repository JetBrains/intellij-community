// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

private fun myFixture(): TestFixture<MyAnnotation?> = testFixture { context ->
  initialized(context.findAnnotation(MyAnnotation::class.java)) { }
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
private annotation class MyAnnotation(val x: Int = 0)

private fun doTestContextTest(fixture: TestFixture<MyAnnotation?>, expectedValue: Int) {
  val annotation = fixture.get()
  assertNotNull(annotation)
  assertEquals(expectedValue, annotation!!.x)
}

@MyAnnotation(x = 1)
@TestFixtures
class FixtureContextTest {
  companion object {
    @JvmStatic
    private val classLevelFixture = myFixture()
  }

  private val testLevelFixture = myFixture()

  @Test
  fun `class level annotation with class level fixture`() = doTestContextTest(classLevelFixture, 1)

  @Test
  fun `class level annotation with test level fixture`() = doTestContextTest(testLevelFixture, 1)

  @Test
  @MyAnnotation(x = 2)
  fun `test level annotation with class level fixture`() = doTestContextTest(classLevelFixture, 1)

  @Test
  @MyAnnotation(x = 2)
  fun `test level annotation with test level fixture`() = doTestContextTest(testLevelFixture, 2)

  @TestFactory
  @MyAnnotation(x = 3)
  fun `dynamic test`(): List<DynamicTest> {
    return buildList {
      repeat(3) {
        add(dynamicTest("test $it") {
          doTestContextTest(testLevelFixture, 3)
        })
      }
    }
  }

  @Nested
  @MyAnnotation(x = 4)
  inner class InnerClass1 {
    @Test
    fun `inner class level annotation with outer class level fixture`() = doTestContextTest(classLevelFixture, 1)
  }

  @Nested
  inner class InnerClass2 {
    private val testLevelFixture = myFixture()

    @Test
    fun `outer class level annotation with test level fixture`() = doTestContextTest(classLevelFixture, 1)

    @Test
    @MyAnnotation(x = 5)
    fun `test level annotation with inner test level fixture`() = doTestContextTest(testLevelFixture, 5)
  }
}