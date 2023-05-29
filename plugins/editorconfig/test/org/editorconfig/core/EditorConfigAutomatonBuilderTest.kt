// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.core

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dk.brics.automaton.Automaton
import org.editorconfig.language.psi.EditorConfigSection
import org.junit.Assert

class EditorConfigAutomatonBuilderTest : BasePlatformTestCase() {
  private fun automatonFor(glob: String): Automaton {
    val file = myFixture.configureByText(".editorconfig", "[$glob]")
    val section = PsiTreeUtil.findChildOfType(file, EditorConfigSection::class.java)!!
    return EditorConfigAutomatonBuilder.globToAutomaton(section.header.pattern, "/")
  }

  fun `test simple`() {
    automatonFor("hello?*[abc]").run {
      assertAccepts("Star can match empty string", "/helloxa")
      assertRejects("Automaton is expecting absolute paths", "helloxa")
      assertAccepts("/some/sort/of/a/folder/hello0123456789c")
      assertRejects("Question mark requires a single character", "/helloa")
    }
  }

  fun `test char class negation`() {
    automatonFor("abc[!abc]").run {
      assertAccepts("/abcd")
      assertRejects("/abc")
      assertRejects("/")
      assertRejects("/abcc")
    }
  }

  fun `test enumeration pattern`() {
    automatonFor("{*.cs,*.vb}").run {
      assertAccepts("/a.cs")
      assertAccepts("/b.cs")
      assertRejects("/")
      assertRejects("/abc")
      assertAccepts("/will/match/in/any/folder/c.vb")
    }
  }

  fun `test prefixed enumeration pattern`() {
    val automaton = automatonFor("*.{cs,vb}")
    Assert.assertEquals(automaton, automatonFor("{*.cs,*.vb}"))
  }

  fun `test nested enumeration pattern`() {
    automatonFor("*.{{c,h},{cpp,hpp}}").run {
      assertAccepts("/a.c")
      assertAccepts("/a.h")
      assertAccepts("/a.cpp")
      assertAccepts("/a.hpp")
      assertRejects("/a.{c,h}")
    }
  }

  fun `test leading spaces inside enumeration patterns are not ignored`() {
    automatonFor("{a, b}").run {
      assertAccepts("/a")
      assertAccepts("/ b")
      assertRejects("/b")
    }
  }

  fun `test spaces in the middle of an enumerated pattern are not ignored`() {
    automatonFor("{a,b c}").run {
      assertAccepts("/b c")
      assertRejects("/bc")
    }
  }

  fun `test any-path pattern together with enumeration pattern`() {
    automatonFor("/a/**/{*.c,*.h}").run {
      assertAccepts("/a/b.c")
      assertAccepts("/a/b/c/d/e.c")
      assertRejects("/ab.c")
      assertRejects("/b/c.c")
    }
  }

  fun `_test interval pattern`() {
    automatonFor("{*.c,{1..20}}").run {
      assertAccepts("/foo.c")
      assertAccepts("/5")
      assertRejects("/25")
    }
  }

  companion object {
    fun Automaton.assertAccepts(string: String) = Assert.assertTrue(this.run(string))
    fun Automaton.assertAccepts(msg: String, string: String) = Assert.assertTrue(msg, this.run(string))
    fun Automaton.assertRejects(string: String) = Assert.assertFalse(this.run(string))
    fun Automaton.assertRejects(msg: String, string: String) = Assert.assertFalse(msg, this.run(string))
  }
}