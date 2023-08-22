// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

import junit.framework.TestCase.*
import org.jetbrains.kotlin.idea.gradleTooling.reflect.*
import org.junit.Test
import java.net.URLClassLoader

class CallReflectiveTest {

    private object StaticTestObject {
        @JvmStatic
        @Suppress("unused") // Used to test static reflection!
        fun staticTestMethod(x: Int, y: Int) = x + y
    }

    class TestLogger : ReflectionLogger {
        val messages = mutableListOf<String>()
        val exceptions = mutableListOf<Throwable?>()

        override fun logIssue(message: String, exception: Throwable?) {
            messages.add(message)
            exceptions.add(exception)
        }
    }

    private val logger = TestLogger()

    @Test
    fun `call get property`() {
        class Tested(@Suppress("unused") val myProperty: String)

        val instance = Tested("abc")
        assertEquals("abc", instance.callReflectiveGetter("getMyProperty", logger))
    }

    @Test
    fun `call get missing property`() {
        class Tested(@Suppress("unused") val myProperty: String)

        val instance = Tested("abc")
        assertNull(instance.callReflectiveGetter("getWrongPropertyName", logger))
        assertEquals(1, logger.messages.size)
    }

    @Test
    fun `call property with wrong return type`() {
        class Tested(@Suppress("unused") val myProperty: String)

        val instance = Tested("abc")
        assertNull(instance.callReflective("getMyProperty", parameters(), returnType<Int>(), logger))
        assertEquals("Expected single issue being reported", 1, logger.messages.size)
        val message = logger.messages.single()
        assertTrue("Expected logged message to mention 'String'", "String" in message)
        assertTrue("Expected logged message to mention 'Int'", "Int" in message)
    }

    @Test
    fun `call function`() {
        class Tested {
            @Suppress("unused")
            fun addTwo(value: Int) = value + 2

            @Suppress("unused")
            fun minus(first: Int, second: Int) = first - second
        }

        val instance = Tested()
        assertEquals(4, instance.callReflective("addTwo", parameters(parameter(2)), returnType<Int>(), logger))
        assertEquals(0, logger.messages.size)

        assertEquals(3, instance.callReflective("minus", parameters(parameter(6), parameter(3)), returnType<Int>(), logger))
        assertEquals(0, logger.messages.size)
    }

    @Test
    fun `call function with null value (int) parameter`() {
        class Tested {
            @Suppress("unused")
            fun orZero(value: Int?): Int = value ?: 0
        }

        val instance = Tested()
        assertEquals(0, instance.callReflective("orZero", parameters(parameter<Int?>(null)), returnType<Int>(), logger))
        assertEquals(0, logger.messages.size)
    }

    @Test
    fun `call function with null value (string) parameter`() {
        class Tested {
            @Suppress("unused")
            fun orEmpty(value: String?) = value ?: ""
        }

        val instance = Tested()
        assertEquals("", instance.callReflective("orEmpty", parameters(parameter<String?>(null)), returnType<String>(), logger))
        assertEquals(0, logger.messages.size)
    }

    @Test
    fun `call static method - using Class reference`() {
        val static = Static(StaticTestObject.javaClass)
        assertEquals(
            5, static.callReflective("staticTestMethod", parameters(parameter(2), parameter(3)), returnType<Int>(), logger)
        )
    }

    @Test
    fun `call static method - non-existing className`() {
        val static = Static("this.does.not.exist", logger)
        assertNull(static)
        assertEquals("Expected single issue being reported", 1, logger.messages.size)
    }

    @Test
    fun `call static method - using className`() {
        val static = Static(StaticTestObject.javaClass.name, logger)
        assertNotNull(logger.messages.joinToString(), static)
        static!!

        assertEquals(
            5, static.callReflective("staticTestMethod", parameters(parameter(2), parameter(3)), returnType<Int>(), logger)
        )
    }

    @Test
    fun `call static method - using custom ClassLoader`() {
        val thisClassLoader = this.javaClass.classLoader
        val customClassLoader = URLClassLoader.newInstance(emptyArray(), thisClassLoader)

        val static = Static(StaticTestObject.javaClass.name, customClassLoader, logger)
        assertNotNull(logger.messages.joinToString(), static)
        static!!

        assertEquals(
            5, static.callReflective("staticTestMethod", parameters(parameter(2), parameter(3)), returnType<Int>(), logger)
        )
    }
}


