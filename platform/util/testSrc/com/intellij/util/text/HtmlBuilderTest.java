// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.util.containers.ContainerUtil;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class HtmlBuilderTest {
  @Test
  public void create() {
    assertEquals("", new HtmlBuilder().toString());
  }

  @Test
  public void isEmpty() {
    assertTrue(new HtmlBuilder().isEmpty());
    assertTrue(new HtmlBuilder().append("").isEmpty());
    assertTrue(new HtmlBuilder().appendRaw("").isEmpty());
    assertFalse(new HtmlBuilder().append("foo").isEmpty());
  }
  
  @Test
  public void append() {
    assertEquals("hello world!", new HtmlBuilder().append("hello ").append("world!").toString());
    assertEquals("&lt;click here&gt;", new HtmlBuilder().append("<click here>").toString());
    assertEquals("<br/>&lt;click here&gt;", new HtmlBuilder().append(HtmlChunk.br()).append("<click here>").toString());
    assertEquals("1234", new HtmlBuilder().append("1").append(new HtmlBuilder().append("2").append("3")).append("4").toString());
  }
  
  @Test
  public void appendLink() {
    assertEquals("<a href=\"url\">click</a> for more info", new HtmlBuilder().appendLink("url", "click").append(" for more info").toString());
  }
  
  @Test
  public void appendWithSeparators() {
    assertEquals("", new HtmlBuilder().appendWithSeparators(HtmlChunk.br(), Collections.emptyList()).toString());
    String[] data = {"foo", "bar", "baz"};
    String html = new HtmlBuilder().appendWithSeparators(HtmlChunk.br(), ContainerUtil.map(data, d -> HtmlChunk.link(d, d))).toString();
    assertEquals("<a href=\"foo\">foo</a><br/><a href=\"bar\">bar</a><br/><a href=\"baz\">baz</a>", html);
  }
  
  @Test
  public void wrapWith() {
    assertEquals("<html>Click <a href=\"foo\">here</a></html>", 
                 new HtmlBuilder().append("Click ").appendLink("foo", "here").wrapWith("html").toString());
    assertEquals("<div style=\"color:blue\">&amp;&amp;</div>", new HtmlBuilder().append("&&").wrapWith(HtmlChunk.div().style("color:blue")).toString());
  }
  
  @Test
  public void wrapWithHtmlBody() {
    assertEquals("<html><body>Hello</body></html>", new HtmlBuilder().append("Hello").wrapWithHtmlBody().toString());
  }
  
  @Test
  public void toFragment() {
    HtmlChunk fragment = new HtmlBuilder().appendLink("1", "1").appendLink("2", "2").toFragment();
    assertEquals("<a href=\"1\">1</a><a href=\"2\">2</a>", fragment.toString());
  }
}
