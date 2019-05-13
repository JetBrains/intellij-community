/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Sergey.Malenkov
 */
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
