/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util.text;

import com.intellij.util.containers.ContainerUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * @author Sergey Simonchik
 */
public class MarkdownUtilTest {

  @Test
  public void testReplaceHeaders() {
    List<String> markdown = ContainerUtil.newArrayList("# Hello1", "## Hello2##", "### Hello3#");
    MarkdownUtil.replaceHeaders(markdown);
    Assert.assertEquals(ContainerUtil.newArrayList("<h1>Hello1</h1>", "<h2>Hello2</h2>", "<h3>Hello3</h3>"), markdown);
  }

  @Test
  public void testRemoveImage() {
    List<String> markdown = Arrays.asList(
      "![logo](http://localhost/logo.png)",
      "Hello, [node](http://nodejs.org). [![Build Status](https://secure.travis-ci.org/visionmedia/express.png)](http://travis-ci.org/visionmedia/express) [![Dependency Status](https://gemnasium.com/visionmedia/express.png)](https://gemnasium.com/visionmedia/express)"
    );
    MarkdownUtil.removeImages(markdown);
    Assert.assertEquals(Arrays.asList(
      "",
      "Hello, [node](http://nodejs.org).  "
    ), markdown);
  }

  @Test
  public void testRemoveImageEdgeCase() {
    List<String> markdown = Arrays.asList("[![logo](http://localhost/logo.png)]");
    MarkdownUtil.removeImages(markdown);
    Assert.assertEquals(Arrays.asList("[]"),markdown);
  }

  @Test
  public void testReplaceCodeBlocks() {
    List<String> markdown = Arrays.asList(" Create the app:",
                                          "",
                                          "    $ npm install -g express",
                                          "    $ express /tmp/foo && cd /tmp/foo");
    MarkdownUtil.replaceCodeBlock(markdown);
    Assert.assertEquals(Arrays.asList(" Create the app:",
                                      "",
                                      "<pre><code>$ npm install -g express",
                                      "$ express /tmp/foo && cd /tmp/foo</code></pre>"), markdown);
  }

  @Test
  public void testReplaceCodeBlocks2() {
    List<String> markdown = Arrays.asList(
      "   text",
      "    code block",
      "```",
      " code block too",
      "```",
      "simple text",
      "    $ code",
      "\t$ code continues",
      "code done"
    );
    MarkdownUtil.replaceCodeBlock(markdown);
    Assert.assertEquals(
      Arrays.asList(
        "   text",
        "<pre><code>code block</code></pre>",
        "<pre><code>",
        " code block too",
        "</code></pre>",
        "simple text",
        "<pre><code>$ code",
        "$ code continues</code></pre>",
        "code done"
      ),
      markdown
    );
  }

  @Test
  public void testReplaceLists1() {
    List<String> markdown = Arrays.asList(
      "*   Red",
      "*   Green",
      "*   Blue"
    );
    MarkdownUtil.generateLists(markdown);
    Assert.assertEquals(
      Arrays.asList(
        "<ul><li>Red</li>",
        "<li>Green</li>",
        "<li>Blue</li></ul>"
      ),
      markdown
    );
  }

  @Test
  public void testReplaceLists2() {
    List<String> markdown = Arrays.asList(
      "1.   Red",
      "",
      "2.   Blue"
    );
    MarkdownUtil.generateLists(markdown);
    Assert.assertEquals(
      Arrays.asList(
        "<ol><li>Red</li>",
        "",
        "<li>Blue</li></ol>"
      ),
      markdown
    );
  }

  @Test
  public void testReplaceLists3() {
    List<String> markdown = Arrays.asList(
      "1986\\. What a great season."
    );
    MarkdownUtil.generateLists(markdown);
    Assert.assertEquals(
      Arrays.asList(
        "1986\\. What a great season."
      ),
      markdown
    );
  }

  @Test
  public void testReplaceLists4() {
    List<String> markdown = Arrays.asList(
      "+ one two",
      "three",
      "",
      " four",
      "+ five",
      "six",
      "",
      "seven",
      "",
      "+ eight"
    );
    MarkdownUtil.generateLists(markdown);
    Assert.assertEquals(
      Arrays.asList(
        "<ul><li>one two",
        "three",
        "",
        "four</li>",
        "<li>five",
        "six</li></ul>",
        "",
        "seven",
        "",
        "<ul><li>eight</li></ul>"
      ),
      markdown
    );
  }

}
