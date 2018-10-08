// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.intellilang.instrumentation

import com.intellij.compiler.instrumentation.FailSafeClassReader
import com.intellij.compiler.instrumentation.InstrumentationClassFinder
import com.intellij.compiler.instrumentation.InstrumenterClassWriter
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ExceptionUtil
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Pattern
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.lang.reflect.*

class PatternInstrumenterTest {
  @Test
  fun simpleReturn() {
    val testClass = loadClass()
    val method = testClass.getMethod("simpleReturn")
    assertFails(method)
  }

  @Test fun multiReturn() {
    val testClass = loadClass()
    val method = testClass.getMethod("multiReturn", Int::class.java)
    assertFails(method, 1)
    assertFails(method, 2)
  }

  @Test fun simpleParam() {
    val testClass = loadClass()
    val method = testClass.getMethod("simpleParam", String::class.java, String::class.java)
    assertFails(method, "0", "-")
  }

  @Test fun staticParam() {
    val testClass = loadClass()
    val method = testClass.getMethod("staticParam", String::class.java, String::class.java)
    assertFails(method, "0", "-")
  }

  @Test fun constructorParam() {
    val testClass = loadClass()
    val method = testClass.getConstructor(String::class.java, String::class.java)
    assertFails(method, "0", "-")
  }

  @Test fun longParam() {
    val testClass = loadClass()
    val method = testClass.getMethod("longParam", Long::class.java, String::class.java)
    assertFails(method, 0L, "-")
  }

  @Test fun doubleParam() {
    val testClass = loadClass()
    val method = testClass.getMethod("doubleParam", Double::class.java, String::class.java)
    assertFails(method, 0.0, "-")
  }

  @Test fun enumConstructor() {
    val testClass = loadClass("TestEnum")
    assertEquals("V1", testClass.getField("V1").get(null).toString())
    assertEquals("V2", testClass.getField("V2").get(null).toString())
  }

  @Test fun groovyEnumConstructor() {
    val testClass = loadClass("TestGrEnum")
    assertEquals("G1", testClass.getField("G1").get(null).toString())
    assertEquals("G2", testClass.getField("G2").get(null).toString())
  }

  @Test fun staticNestedClass() {
    val testClass = loadClass()
    val method = testClass.getMethod("createNested", String::class.java, String::class.java)
    assertFails(method, "0", "-")
  }

  @Test fun innerClass() {
    val testClass = loadClass()
    val method = testClass.getMethod("createInner", String::class.java, String::class.java)
    assertFails(method, "0", "-")
  }

  @Test fun groovyInnerClass() {
    val testClass = loadClass("TestGrInner")
    val method = testClass.getConstructor(String::class.java, String::class.java)
    assertFails(method, "0", "-")
  }

  @Test fun skipBridgeMethod() {
    val testClass = loadClass()
    try {
      testClass.getMethod("bridgeMethod").invoke(null)
      fail("Method invocation should have failed")
    }
    catch (e: InvocationTargetException) {
      val trace = ExceptionUtil.getThrowableText(e.cause!!)
      assertEquals("Exception should happen in real, non-bridge method: $trace",
                   2, StringUtil.getOccurrenceCount(trace, "at TestClass\$B.get("))
    }
  }

  @Test fun enclosingClass() {
    val testClass = loadClass()
    val obj1 = testClass.getMethod("enclosingStatic").invoke(null)
    assertEquals(testClass, obj1::class.java.enclosingClass)
    val obj2 = testClass.getMethod("enclosingInstance").invoke(testClass.newInstance())
    assertEquals(testClass, obj2::class.java.enclosingClass)
  }

  @Test fun capturedParam() {
    val testClass = loadClass()
    val method = testClass.getMethod("capturedParam", String::class.java)
    method.invoke(null, "0")
    assertFails(method, "-")
  }

  @Test fun metaAnnotation() {
    val testClass = loadClass()
    val method = testClass.getMethod("metaAnnotation")
    assertFails(method)
  }

  @Test fun assertedClass() {
    val testClass = loadClass("TestAssert", InstrumentationType.ASSERT)
    val method = testClass.getMethod("simpleReturn")
    assertFails(method)
  }

  private fun loadClass(name: String = "TestClass", type: InstrumentationType = InstrumentationType.EXCEPTION): Class<*> {
    val testDir = File(PluginPathManager.getPluginHomePath("IntelliLang") + "/intellilang-jps-plugin/testData/patternInstrumenter")

    val roots = IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("jetbrains-annotations")
    val paths = listOf(testDir.path) + roots + listOf(PlatformTestUtil.getRtJarPath())
    val urls = paths.map { p -> File(p).toURI().toURL() }.toTypedArray()
    val finder = InstrumentationClassFinder(urls)

    val loader = MyClassLoader()
    testDir.listFiles().filter { it.name.startsWith(name) && it.name.endsWith(".class") }.sorted().forEach {
      val reader = FailSafeClassReader(it.readBytes())
      val writer = InstrumenterClassWriter(reader, ClassWriter.COMPUTE_FRAMES, finder)
      PatternValidatorBuilder.processClassFile(reader, writer, finder, Pattern::class.java.name, type)
      loader.createClass(it.nameWithoutExtension, writer.toByteArray())
    }
    return Class.forName(name, false, loader)
  }

  private fun assertFails(member: Member, vararg args: Any) {
    try {
      when {
        member is Constructor<*> -> member.newInstance(*args)
        Modifier.isStatic(member.modifiers) -> (member as Method).invoke(null, *args)
        else -> {
          val instance = member.declaringClass.newInstance()
          (member as Method).invoke(instance, *args)
        }
      }
      fail("Method invocation should have failed")
    }
    catch (e: InvocationTargetException) {
      assertThat(e.cause?.message).endsWith(" does not match pattern \\d+")
    }
  }

  private class MyClassLoader : ClassLoader(PatternInstrumenterTest::class.java.classLoader) {
    fun createClass(name: String, data: ByteArray): Class<*> = defineClass(name, data, 0, data.size)
  }
}