// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("unused")

package org.jetbrains.kotlin.idea.gradleTooling

import org.jetbrains.kotlin.idea.gradleTooling.reflect.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class CallReflectiveTest {

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
        class Tested(val myProperty: String)

        val instance = Tested("abc")
        assertEquals("abc", instance.callReflectiveGetter("getMyProperty", logger))
    }

    @Test
    fun `call get missing property`() {
        class Tested(val myProperty: String)

        val instance = Tested("abc")
        assertNull(instance.callReflectiveGetter("getWrongPropertyName", logger))
        assertEquals(1, logger.messages.size)
    }

    @Test
    fun `call property with wrong return type`() {
        class Tested(val myProperty: String)

        val instance = Tested("abc")
        assertNull(instance.callReflective("getMyProperty", parameters(), returnType<Int>(), logger))
        assertEquals(1, logger.messages.size, "Expected single issue being reported")
        val message = logger.messages.single()
        assertTrue("String" in message, "Expected logged message to mention 'String'")
        assertTrue("Int" in message, "Expected logged message to mention 'Int'")
    }

    @Test
    fun `call function`() {
        class Tested {
            fun addTwo(value: Int) = value + 2
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
            fun orZero(value: Int?): Int = value ?: 0
        }

        val instance = Tested()
        assertEquals(0, instance.callReflective("orZero", parameters(parameter<Int?>(null)), returnType<Int>(), logger))
        assertEquals(0, logger.messages.size)
    }

    @Test
    fun `call function with null value (string) parameter`() {
        class Tested {
            fun orEmpty(value: String?) = value ?: ""
        }

        val instance = Tested()
        assertEquals("", instance.callReflective("orEmpty", parameters(parameter<String?>(null)), returnType<String>(), logger))
        assertEquals(0, logger.messages.size)
    }
}

