// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.intellilang.instrumentation

import com.intellij.compiler.instrumentation.FailSafeClassReader
import com.intellij.compiler.instrumentation.InstrumentationClassFinder
import com.intellij.compiler.instrumentation.InstrumenterClassWriter
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.ExceptionUtil
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File
import java.lang.reflect.*

class PatternInstrumenterTest {
  @Rule @JvmField val tempDir = TempDirectory()
  @Rule @JvmField val testName = TestName()

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
    val testClass = loadClass()
    assertEquals("V1", testClass.getField("V1").get(null).toString())
    assertEquals("V2", testClass.getField("V2").get(null).toString())
  }

  @Test fun groovyEnumConstructor() {
    val testClass = loadClass()
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
    val testClass = loadClass()
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
                   2, StringUtil.getOccurrenceCount(trace, "at SkipBridgeMethod\$B.get("))
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
    val testClass = loadClass(InstrumentationType.ASSERT)
    val method = testClass.getMethod("simpleReturn")
    assertFails(method)
  }

  private fun loadClass(type: InstrumentationType = InstrumentationType.EXCEPTION): Class<*> {
    val testDir = PluginPathManager.getPluginHomePath("IntelliLang") + "/intellilang-jps-plugin/testData/patternInstrumenter/"
    val testName = testName.methodName.capitalize()
    val testFile = IdeaTestUtil.findSourceFile(testDir + testName)
    val classesDir = tempDir.newDirectory("out")
    val rootPaths = IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("jetbrains-annotations")
    IdeaTestUtil.compileFile(testFile, classesDir, "-cp", rootPaths.joinToString(File.pathSeparator))

    val finder = InstrumentationClassFinder((listOf(classesDir.toURI().toURL()) +
                                             rootPaths.map { p -> File(p).toURI().toURL() } +
                                             listOf(PlatformTestUtil.getRtJarURL())).toTypedArray())

    var modified = false
    val loader = MyClassLoader()
    classesDir.listFiles().sorted().forEach {
      val reader = FailSafeClassReader(it.readBytes())
      val flags = InstrumenterClassWriter.getAsmClassWriterFlags(InstrumenterClassWriter.getClassFileVersion(reader))
      val writer = InstrumenterClassWriter(reader, flags, finder)
      modified = modified or PatternValidatorBuilder.processClassFile(reader, writer, finder, Pattern::class.java.name, type)
      loader.createClass(it.nameWithoutExtension, writer.toByteArray())
    }
    assertThat(modified).withFailMessage("Class file not instrumented!").isTrue()
    return Class.forName(testName, false, loader)
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