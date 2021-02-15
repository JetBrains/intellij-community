// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ui.messages.MacMessageHelper;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public class MacMessageTest extends TestCase {
  public void testHtmlMessages() {
    assertHtml(
      "Message Message Message\nTest Test Test\n   Foo <>&'\" Foo",
      "<html>Message <a>Message</a> Message<br>Test <b>Test</b> Test<br>&nbsp;&nbsp;&nbsp;Foo &lt;&gt;&amp;&#39;&quot; Foo</html>");

    assertHtml(
      "No SDK Specified",
      UIUtil.toHtml("No SDK Specified")
    );

    assertHtml("Foo and Bar", "<html>Foo<style></style> and <style>{color: #red}</style>Bar</html>");
  }

  public void testHtmlBuilderMessages() {
    assertHtml(
      "Method FOO overrides a method from MODULE",
      new HtmlBuilder().appendRaw("Method " + HtmlChunk.text("FOO").bold() + " overrides a method from " + HtmlChunk.text("MODULE").bold())
        .wrapWithHtmlBody().toString());

    HtmlChunk.Element text = HtmlChunk.tag("left");
    text = text.addText("Foo Bar ZZZ ");
    text = text.child(HtmlChunk.link("https://google.com", "link"));

    assertHtml("Foo Bar ZZZ link",
               new HtmlBuilder().append(HtmlChunk.tag("font").attr("color", "red").child(text)).wrapWithHtmlBody().toString());
  }

  private static void assertHtml(@NotNull String expected, @NotNull String actual) {
    assertEquals(actual, expected, MacMessageHelper.stripHtmlMessage(actual));
  }
}