// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.refs

import org.jetbrains.plugins.javaFX.fxml.refs.JavaFxExpressionParser.parse
import org.junit.Assert.assertEquals
import org.junit.Test

internal class JavaFxExpressionParserTest {

  @Test
  fun testInvalid() {
    assert(!parse("").syntacticallyValid)
    assert(!parse("1 + ").syntacticallyValid)
    assert(!parse("100 + -").syntacticallyValid)
    assert(!parse("-").syntacticallyValid)
    assert(!parse("+-1").syntacticallyValid)
    assert(!parse("&& myDouble").syntacticallyValid)
    assert(!parse("< 2").syntacticallyValid)
    assert(!parse("a.b + c.d +").syntacticallyValid)
    assert(!parse("a.b + c.d -").syntacticallyValid)
    assert(!parse("123..").syntacticallyValid)
    assert(!parse("a.").syntacticallyValid)
  }

  @Test
  fun testValid() {
    assert(parse("a.b.c.d.e.f").syntacticallyValid)
    assert(parse("a.b.c.d.e.f + h.i.j.k.l").syntacticallyValid)
    assert(parse("null").syntacticallyValid)
    assert(parse("-1").syntacticallyValid)
    assert(parse("!a.b").syntacticallyValid)
    assert(parse("--1").syntacticallyValid)
    assert(parse("------1").syntacticallyValid)
    assert(parse("-a.b").syntacticallyValid)
    assert(parse("100 + -a").syntacticallyValid)
    assert(parse("123.").syntacticallyValid)
  }

  @Test
  fun testSingleChain() {
    assertEquals(1, parse("myField").chains.size)
    assertEquals(1, parse("myField.text").chains.size)
    assertEquals(2, parse("myField.text").chains[0].segments.size)
    assertEquals("myField", parse("myField.text").chains[0].segments[0].name)
    assertEquals("text", parse("myField.text").chains[0].segments[1].name)
  }

  @Test
  fun testMultipleChains() {
    assertEquals(2, parse("f1.text + f2.text").chains.size)
    assertEquals(2, parse("f1.text + f2.text").chains[0].segments.size)
    assertEquals("f1", parse("f1.text + f2.text").chains[0].segments[0].name)
    assertEquals("text", parse("f1.text + f2.text").chains[0].segments[1].name)
    assertEquals("f2", parse("f1.text + f2.text").chains[1].segments[0].name)
    assertEquals("text", parse("f1.text + f2.text").chains[1].segments[1].name)
  }

  @Test
  fun testEntity() {
    // &amp; decodes to &&, which is a valid binary operator
    assert(parse("a &amp;&amp; b").syntacticallyValid)
    assertEquals(2, parse("a &amp;&amp; b").chains.size)

    // &lt; and &gt; decode to < and >
    assert(parse("a &lt; b").syntacticallyValid)
    assert(parse("a &gt; b").syntacticallyValid)

    // &quot; and &apos; decode to " and ', forming a valid string literal
    assert(parse("&quot;hello&quot;").syntacticallyValid)
    assert(parse("&apos;hello&apos;").syntacticallyValid)

    // unknown entity is not decoded, & is treated as invalid operator
    assert(!parse("a &unknown; b").syntacticallyValid)

    // offsets are preserved: segment offset should point to the original position
    assertEquals(0, parse("a &amp;&amp; b").chains[0].segments[0].offsetInBody)
    assertEquals(13, parse("a &amp;&amp; b").chains[1].segments[0].offsetInBody)
  }

}
