// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import org.junit.Assert;
import org.junit.Test;

public class UIUtilTest {
  @Test
  public void testHtmlBody() {
    testHtmlBody("simple text", "simple text");
    testHtmlBody("<body>multi<br>line</body>", "<body>multi\nline</body>");
    testHtmlBody("normal html", "<html><body>normal html</body></html>");
    testHtmlBody("no body", "<html>no body</html>");
    testHtmlBody("no closing tags", "<html><body>no closing tags");
    testHtmlBody("no closing body tag", "<html><body>no closing body tag</html>");
    testHtmlBody("no closing html tag", "<html><body>no closing html tag</body>");
    testHtmlBody("mixed closing tags", "<html><body>mixed closing tags</html></body>");
    testHtmlBody("<!--comment-->", "<html><body><!--comment--></body></html>");
    testHtmlBody("leading comment", "<!--comment--><html><body>leading comment</body></html>");
    testHtmlBody("trailing comment", "<html><body>trailing comment</body></html><!--comment-->");
  }

  private static void testHtmlBody(String expectedBody, String actualHTML) {
    Assert.assertEquals(expectedBody, UIUtil.getHtmlBody(actualHTML));
  }
}
