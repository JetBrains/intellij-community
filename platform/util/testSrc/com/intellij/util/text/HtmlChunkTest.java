// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.util.text.HtmlChunk;
import org.junit.Test;

import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class HtmlChunkTest {
  @Test
  public void text() {
    assertEquals("foo", HtmlChunk.text("foo").toString());
    assertEquals("&lt;a href=&quot;hello&quot;&gt;", HtmlChunk.text("<a href=\"hello\">").toString());
  }

  @Test
  public void raw() {
    assertEquals("foo", HtmlChunk.raw("foo").toString());
    assertEquals("<a href=\"hello\">", HtmlChunk.raw("<a href=\"hello\">").toString());
  }

  @Test
  public void nbsp() {
    assertEquals("&nbsp;", HtmlChunk.nbsp().toString());
    assertEquals("&nbsp;&nbsp;&nbsp;", HtmlChunk.nbsp(3).toString());
  }

  @Test
  public void link() {
    assertEquals("<a href=\"target\">&lt;Click here&gt;</a>", HtmlChunk.link("target", "<Click here>").toString());
  }
  
  @Test
  public void tag() {
    assertEquals("<b/>", HtmlChunk.tag("b").toString());
    assertEquals("<br/>", HtmlChunk.br().toString());
  }

  @Test
  public void div() {
    assertEquals("<div/>", HtmlChunk.div().toString());
    assertEquals("<div style=\"color: blue\"/>", HtmlChunk.div("color: blue").toString());
  }
  
  @Test
  public void attr() {
    assertEquals("<p align=\"left\"/>", HtmlChunk.tag("p").attr("align", "left").toString());
    assertEquals("<p align=\"right\"/>", HtmlChunk.tag("p").attr("align", "left").attr("align", "right").toString());
  }
  
  @Test
  public void children() {
    assertEquals("<p align=\"left\"><br/></p>", HtmlChunk.tag("p").attr("align", "left").child(HtmlChunk.br()).toString());
    assertEquals("<p>&lt;hello&gt;</p>", HtmlChunk.tag("p").child(HtmlChunk.text("<hello>")).toString());
    assertEquals("<p>&lt;hello&gt;</p>", HtmlChunk.tag("p").addText("<hello>").toString());
    assertEquals("<p><a href=\"ref\">&lt;hello&gt;</a></p>", HtmlChunk.tag("p").child(HtmlChunk.link("ref", "<hello>")).toString());
  }
  
  @Test
  public void wrapWith() {
    assertEquals("<p>hello</p>", HtmlChunk.text("hello").wrapWith("p").toString());
    assertEquals("<b>hello</b>", HtmlChunk.text("hello").bold().toString());
    assertEquals("<i>hello</i>", HtmlChunk.text("hello").italic().toString());
  }
  
  @Test
  public void toFragment() {
    HtmlChunk fragment = Stream.of("foo", "bar", "baz").map(t -> HtmlChunk.link(t, t)).collect(HtmlChunk.toFragment());
    assertEquals("<a href=\"foo\">foo</a><a href=\"bar\">bar</a><a href=\"baz\">baz</a>", fragment.toString());
    fragment = Stream.of("foo", "bar", "baz").map(HtmlChunk::text).collect(HtmlChunk.toFragment(HtmlChunk.br()));
    assertEquals("foo<br/>bar<br/>baz", fragment.toString());
  }
}
