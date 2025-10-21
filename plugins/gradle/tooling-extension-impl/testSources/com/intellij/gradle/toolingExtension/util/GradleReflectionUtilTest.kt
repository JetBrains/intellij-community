// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.util

import com.intellij.gradle.toolingExtension.util.GradleReflectionUtil.*
import org.gradle.api.Project
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows

class GradleReflectionUtilTest {

  class TestObject

  open class ParentClass {
    fun parentClassMethod() = Unit
    private fun parentClassPrivateMethod() = Unit
  }

  class HasMethodTestObject : ParentClass() {
    fun publicMethod(): Unit = Unit
    fun publicMethodWithFewArguments(string: String, long: Long): Unit = Unit
    protected fun protectedMethod(string: String): Unit = Unit
    private fun privateMethod(): Unit = Unit
  }

  class GetMethodTestObject : ParentClass() {
    fun publicMethod(): Unit = Unit
    fun publicMethodWithFewArguments(string: String, long: Long): Unit = Unit
    protected fun protectedMethod(string: String): Unit = Unit
    private fun privateMethod(): Unit = Unit
  }

  class InvokeMethodTestObject {
    var invoked: Boolean = false
    var invocationValue: Any? = null

    fun invoke() {
      if (invoked) {
        throw IllegalStateException("The method already was invoked!")
      }
      invoked = true
    }

    fun invokeWithArgument(string: String) {
      invoke()
      invocationValue = string
    }
  }

  class GetValueTestObject {
    fun problemSolver() = "Solved"
    private fun privateMethod(): Int = 42
  }

  class SetValueTestObject {
    var invoked: Boolean = false
    var invocationValue: Any? = null

    fun testSetter(string: String) {
      if (invoked) {
        throw IllegalStateException("The method already was invoked!")
      }
      invoked = true
      invocationValue = string
    }

    private fun privateSetter(string: String) {
      throw IllegalStateException("The method shouldn't be not called!")
    }
  }

  @Test
  fun testHasMethod() {
    val target = HasMethodTestObject()
    assertTrue(hasMethod(target, "publicMethod"))
    assertFalse(hasMethod(target, "publicMethod", Long::class.java))

    assertFalse(hasMethod(target, "publicMethodWithFewArguments"))
    assertTrue(hasMethod(target, "publicMethodWithFewArguments", String::class.java, Long::class.java))

    assertFalse(hasMethod(target, "protectedMethod"))
    assertFalse(hasMethod(target, "protectedMethod", String::class.java))

    assertFalse(hasMethod(target, "privateMethod"))
    assertFalse(hasMethod(target, "protectedMethod", Int::class.java))

    assertTrue(hasMethod(target, "parentClassMethod"))
    assertFalse(hasMethod(target, "parentClassPrivateMethod"))
    assertFalse(hasMethod(target, "parentClassMethod", String::class.java))
    assertFalse(hasMethod(target, "parentClassPrivateMethod", String::class.java))
  }

  @Test
  fun testGetMethod() {
    assertNotNull(getMethod(GetMethodTestObject::class.java, "publicMethod"))
    assertNotNull(getMethod(GetMethodTestObject::class.java, "publicMethodWithFewArguments", String::class.java, Long::class.java))
    assertNotNull(getMethod(GetMethodTestObject::class.java, "parentClassMethod"))

    assertThrows<IllegalStateException> { getMethod(GetMethodTestObject::class.java, "publicMethod2") }
    assertThrows<IllegalStateException> { getMethod(GetMethodTestObject::class.java, "publicMethodWithFewArguments", String::class.java, Int::class.java) }
    assertThrows<IllegalStateException> { getMethod(GetMethodTestObject::class.java, "protectedMethod") }
    assertThrows<IllegalStateException> { getMethod(GetMethodTestObject::class.java, "privateMethod") }
    assertThrows<IllegalStateException> { getMethod(GetMethodTestObject::class.java, "parentClassPrivateMethod") }
  }

  @Test
  fun testInvokeMethod() {
    val target = InvokeMethodTestObject()
    val method = getMethod(InvokeMethodTestObject::class.java, "invoke")
    assertNull(invokeMethod(method, target))
    assertTrue(target.invoked)
    assertNull(target.invocationValue)
  }

  @Test
  fun testInvokeMethodWithArgument() {
    val target = InvokeMethodTestObject()
    val method = getMethod(InvokeMethodTestObject::class.java, "invokeWithArgument", String::class.java)
    assertNull(invokeMethod(method, target, "Hello"))
    assertTrue(target.invoked)
    assertEquals("Hello", target.invocationValue)
  }

  @Test
  fun testGetValue() {
    val target = GetValueTestObject()
    assertEquals("Solved", getValue(target, "problemSolver", String::class.java))
    assertThrows<IllegalStateException> { getValue(target, "problemSolver", Int::class.java) }
    assertThrows<IllegalStateException> { getValue(target, "privateMethod", Int::class.java) }
  }

  @Test
  fun testGetPrivateValue() {
    val target = GetValueTestObject()
    assertEquals("Solved", getPrivateValue(target, "problemSolver", String::class.java))
    assertThrows<IllegalStateException> { getPrivateValue(target, "problemSolver", Int::class.java) }

    assertEquals(42, getPrivateValue(target, "privateMethod", Integer::class.java))
    assertThrows<IllegalStateException> { getPrivateValue(target, "privateMethod", String::class.java) }
  }

  @Test
  fun testSetValue() {
    val target = SetValueTestObject()
    setValue(target, "testSetter", String::class.java, "Hello")
    assertTrue(target.invoked)
    assertEquals("Hello", target.invocationValue)
  }

  @Test
  fun testSetPrivateValue() {
    val target = SetValueTestObject()
    assertThrows<IllegalStateException> { setValue(target, "privateSetter", String::class.java, "Hello") }
  }

  @Test
  fun testFindClassForName() {
    assertSame(TestObject::class.java, findClassForName($$"com.intellij.gradle.toolingExtension.util.GradleReflectionUtilTest$TestObject"))
    assertNull(findClassForName("org.example.something.Hello"))
  }

  @Test
  fun testLoadClassOrNull() {
    assertSame(TestObject::class.java,
               loadClassOrNull(
                 GradleReflectionUtilTest::class.java.classLoader,
                 $$"com.intellij.gradle.toolingExtension.util.GradleReflectionUtilTest$TestObject"
               )
    )
    assertNull(loadClassOrNull(GradleReflectionUtilTest::class.java.classLoader, "org.example.something.Hello"))
  }

  @Test
  fun testGetGradleClass() {
    assertSame(Project::class.java, getGradleClass("org.gradle.api.Project"))
    assertThrows<IllegalStateException> { getGradleClass("org.example.something.something.Hello") }
  }
}
