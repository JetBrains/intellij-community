// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal class JnaLoadFailureDiagnosticTest {
  private val noLoad: (String) -> Unit = { throw AssertionError("no load expected") }

  @Test
  fun `the diagnostic shows the error of the load`(@TempDir directory: Path) {
    Files.writeString(directory.resolve("libjnidispatch.jnilib"), "not a library")
    val loaded = ArrayList<String>()
    val text = describeJnaLoadFailure(properties(directory.toString()), fileName = "libjnidispatch.jnilib", load = {
      loaded.add(it)
      throw UnsatisfiedLinkError("Native Library $it already loaded in another classloader")
    })

    assertThat(loaded).containsExactly(directory.resolve("libjnidispatch.jnilib").toString())
    assertThat(text)
      .contains("jna.boot.library.path=$directory")
      .contains("jna.noclasspath=true")
      .contains("jnidispatch.path=null")
      .contains("[libjnidispatch.jnilib]")
      .contains("size=13")
      .contains("already loaded in another classloader")
  }

  @Test
  fun `the diagnostic tries the dylib name after the jnilib name`(@TempDir directory: Path) {
    Files.writeString(directory.resolve("libjnidispatch.dylib"), "")
    val loaded = ArrayList<String>()
    val text = describeJnaLoadFailure(properties(directory.toString()), fileName = "libjnidispatch.jnilib", load = { loaded.add(it) })

    assertThat(loaded).containsExactly(directory.resolve("libjnidispatch.dylib").toString())
    assertThat(text).contains("System.load: loaded")
  }

  @Test
  fun `the diagnostic names a missing library`(@TempDir directory: Path) {
    Files.writeString(directory.resolve("other.txt"), "")
    val text = describeJnaLoadFailure(properties(directory.toString()), fileName = "jnidispatch.dll", load = noLoad)

    assertThat(text)
      .contains("[other.txt]")
      .contains("no jnidispatch.dll in the boot path")
  }

  @Test
  fun `the diagnostic names a missing directory`(@TempDir directory: Path) {
    val missing = directory.resolve("missing")
    val text = describeJnaLoadFailure(properties(missing.toString()), fileName = "jnidispatch.dll", load = noLoad)

    assertThat(text).contains("directory $missing: does not exist")
  }

  @Test
  fun `the diagnostic hides the IDE home and the user home`() {
    val s = File.separator
    val userHome = "${s}home${s}someone"
    val ideHome = "$userHome${s}apps${s}IDEA"
    val text = "$ideHome${s}plugins${s}jna-plugin and $userHome${s}x and ${userHome}2"
    assertThat(hideUserPaths(text, ideHome = ideHome, userHome = userHome))
      .isEqualTo("\$IDE_HOME${s}plugins${s}jna-plugin and ~${s}x and ${userHome}2")
  }

  @Test
  fun `the diagnostic keeps the paths when the user home is the root`() {
    val text = "${File.separator}opt${File.separator}idea"
    assertThat(hideUserPaths(text, ideHome = null, userHome = File.separator)).isEqualTo(text)
  }

  @Test
  fun `the first JNA init failure gets the diagnostic once`() {
    val diagnostic = JnaLoadFailureDiagnostic("test")
    val failure = RuntimeException("Cannot load implementation", UnsatisfiedLinkError("Unable to locate JNA native support library"))
    addJnaLoadFailureDiagnostic(failure) { diagnostic }
    addJnaLoadFailureDiagnostic(failure) { diagnostic }
    assertThat(failure.suppressed).containsExactly(diagnostic)
  }

  @Test
  fun `a later JNA use gets the diagnostic`() {
    val diagnostic = JnaLoadFailureDiagnostic("test")
    val failure = RuntimeException(
      "Cannot load implementation",
      NoClassDefFoundError("Could not initialize class com.sun.jna.Native").initCause(ExceptionInInitializerError()),
    )
    addJnaLoadFailureDiagnostic(failure) { diagnostic }
    assertThat(failure.suppressed).containsExactly(diagnostic)
  }

  @Test
  fun `a failure without a JNA init failure gets no diagnostic`() {
    for (failure in listOf(
      RuntimeException("unrelated", IllegalStateException()),
      RuntimeException("pty", UnsatisfiedLinkError("Unable to load library 'pty'")),
      RuntimeException("class", NoClassDefFoundError("com/pty4j/unix/Pty")),
    )) {
      addJnaLoadFailureDiagnostic(failure) { throw AssertionError("no diagnostic expected") }
      assertThat(failure.suppressed).isEmpty()
    }
  }

  private fun properties(bootPath: String): (String) -> String? = {
    when (it) {
      "jna.boot.library.path" -> bootPath
      "jna.nosys", "jna.noclasspath" -> "true"
      else -> null
    }
  }
}
