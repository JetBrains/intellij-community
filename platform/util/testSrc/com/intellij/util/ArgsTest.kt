// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.platform.util.ArgsParser
import com.intellij.util.io.write
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ArgsTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun testInt() {
    val parser = ArgsParser(listOf("--test=123"))
    val all by parser.arg("test", "").int { 42 }
    assertEquals(123, all)
  }

  @Test
  fun testIntOptional() {
    val parser = ArgsParser(listOf(""))
    val all by parser.arg("test", "").optional().int { 42 }
    assertEquals(42, all)
  }

  @Test
  fun testIntOrNull() {
    val parser = ArgsParser(listOf(""))
    val all by parser.arg("test", "").toIntOrNull()
    assertEquals(null, all)
  }

  @Test
  fun testIntOrNull2() {
    val parser = ArgsParser(listOf("--test=234"))
    val all by parser.arg("test", "").toIntOrNull()
    assertEquals(234, all)
  }

  @Test
  fun testStrings() {
    val parser = ArgsParser(listOf(""))
    val all by parser.arg("test", "").strings()
    assertEquals(0, all.size)
  }

  @Test
  fun testFiles() {
    val parser = ArgsParser(listOf(""))
    val all by parser.arg("test", "").files()
    assertEquals(0, all.size)
  }

  @Test
  fun testNotEmptyFiles() {
    val parser = ArgsParser(listOf(""))
    assertThrows<IllegalArgumentException> {
      val notEmpty by parser.arg("test", "").notEmptyFiles()
      println(notEmpty)
    }
  }

  @Test
  fun testNotEmptyStrings() {
    val parser = ArgsParser(listOf(""))
    assertThrows<IllegalArgumentException> {
      val notEmpty by parser.arg("test", "").notEmptyStrings()
      println(notEmpty)
    }
  }

  @Test
  fun test_args_file() {
    val argsFile = tempDir.resolve("args-file")
    argsFile.write("--test=123")
    val parser = ArgsParser(listOf("--@args-file=$argsFile", "--foo=bar"))
    val test by parser.arg("test", "").string()
    assertEquals("123", test)

    val foo by parser.arg("foo", "").string()
    assertEquals("bar", foo)
  }

  @Test
  fun test_args_from_multiple_files() {
    val oneFile = tempDir.resolve("args-file-one")
    oneFile.write("--foo=123")
    val twoFile = tempDir.resolve("args-file-two")
    twoFile.write("--bar=true")
    val parser = ArgsParser(listOf("--@args-file=$oneFile", "--@args-file=$twoFile", "--baz=baz"))

    val foo by parser.arg("foo", "").string()
    assertEquals("123", foo)

    val bar by parser.arg("bar", "").boolean()
    assertEquals(true, bar)

    val baz by parser.arg("baz", "").string()
    assertEquals("baz", baz)
  }

  @Test
  fun test_arg() {
    val parser = ArgsParser(listOf("--test=123"))
    val text by parser.arg("test", "").string()
    assertEquals("123", text)
  }

  @Test
  fun test_bool() {
    val parser = ArgsParser(listOf("--test=true"))
    val text by parser.arg("test", "").boolean()
    assertEquals(true, text)
  }

  @Test
  fun test_args() {
    val parser = ArgsParser(listOf("--test=123", "--test=456", "--test=789", "--foo=bar"))
    val text by parser.arg("test", "").strings()
    assertEquals(listOf("123", "456", "789"), text)
  }

  @Test
  fun test_file() {
    val someFile = tempDir.resolve("someFile")
    val parser = ArgsParser(listOf("--file=$someFile"))
    val file by parser.arg("file", "").file()
    assertEquals(someFile, file)
  }

  @Test
  fun test_arg_with_default() {
    val parser = ArgsParser(listOf("--test=123"))
    val value by parser.arg("unspecified", "").string { "default" }
    assertEquals("default", value)
  }

  @Test
  fun test_arg_not_specified() {
    val parser = ArgsParser(listOf("--test=123"))
    assertThrows<IllegalArgumentException> {
      val value by parser.arg("unspecified", "").string()
      println(value)
    }
  }

  @Test
  fun test_boolean_arg_wrong_value() {
    val parser = ArgsParser(listOf("--test=123"))
    assertThrows<IllegalArgumentException> {
      val value by parser.arg("test", "").boolean()
      println(value)
    }
  }

  @Test
  fun test_arg_multiple_values() {
    val parser = ArgsParser(listOf("--test=123", "--test=456"))
    assertThrows<IllegalArgumentException> {
      val value by parser.arg("test", "").string()
      println(value)
    }
  }

  @Test
  fun test_booleanOrNull() {
    val parser = ArgsParser(listOf())
    val value by parser.arg("test", "").booleanOrNull()
    assertNull(value)
  }

  @Test
  fun test_booleanOrNull2() {
    val parser = ArgsParser(listOf("--test=true"))
    val value by parser.arg("test", "").booleanOrNull()
    assertEquals(true, value)
  }

  @Test
  fun test_transform() {
    val parser = ArgsParser(listOf())
    val value by parser.arg("test", "").booleanOrNull().andMap { 42 }
    assertEquals(42, value)
  }

  @Test
  fun test_transform_is_called() {
    val parser = ArgsParser(listOf())
    assertThrows<Throwable> {
      parser.arg("test", "").booleanOrNull().andMap { error("123") }
      parser.tryReadAll()
    }
  }

  @Test
  fun test_apply_is_called() {
    val parser = ArgsParser(listOf())
    assertThrows<Throwable> {
      parser.arg("test", "").booleanOrNull().andApply { error("123") }
      parser.tryReadAll()
    }
  }

  @Test
  fun test_flag_1() {
    val parser = ArgsParser(listOf("--test=true"))
    val value by parser.arg("test", "").flag()
    assertEquals(true, value)
  }

  @Test
  fun test_flag_2() {
    val parser = ArgsParser(listOf("--test"))
    val value by parser.arg("test", "").flag()
    assertEquals(true, value)
  }

  @Test
  fun test_flag_3() {
    val parser = ArgsParser(listOf(""))
    val value by parser.arg("test", "").flag()
    assertEquals(false, value)
  }

  @Test
  fun test_flag_4() {
    val parser = ArgsParser(listOf("--test=false"))
    val value by parser.arg("test", "").flag()
    assertEquals(false, value)
  }
}
