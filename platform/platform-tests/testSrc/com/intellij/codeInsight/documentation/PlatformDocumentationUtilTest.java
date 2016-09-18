/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.documentation;

import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class PlatformDocumentationUtilTest {

  private static final String UNMODIFIED_PREFIX =
    "<html><head><base href=\"file:/dev/_test/idea_brackets/src/org/stefanneuhaus/Main.java\">    <style type=\"text/css\">        #error {            background-color: #eeeeee;            margin-bottom: 10px;        }        p {            margin: 5px 0;        }    </style></head><body><small><b>com.company</b></small><PRE>public class <b>Main</b> extends <a href=\"psi_element://java.lang.Object\"><code>Object</code></a></PRE>";
  private static final String UNMODIFIED_SUFFIX = "</body></html>";

  @Test
  public void multipleOpeningAngleBrackets() {

    final Map<String, String> testCases = new LinkedHashMap<>();

    testCases.put("17 < 42", "17 &lt; 42");
    testCases.put("name << [\"Kirk\", \"Spock\", \"Scotty\"]", "name &lt;&lt; [\"Kirk\", \"Spock\", \"Scotty\"]");
    testCases.put("triple_brackets_lhs <<< rhs", "triple_brackets_lhs &lt;&lt;&lt; rhs");
    testCases.put("four_brackets_lhs <<<< rhs", "four_brackets_lhs &lt;&lt;&lt;&lt; rhs");
    testCases.put("seven_brackets_lhs <<<<<<< rhs", "seven_brackets_lhs &lt;&lt;&lt;&lt;&lt;&lt;&lt; rhs");
    testCases.put("covers the internal bug <\\ workaround", "covers the internal bug <\\ workaround");

    for (final Map.Entry<String, String> testCase : testCases.entrySet()) {
      // given
      final String input = addPrefixAndSuffix(testCase.getKey());
      final String expected = addPrefixAndSuffix(testCase.getValue());

      // when
      final String actual = PlatformDocumentationUtil.fixupText(input);

      // then
      Assert.assertEquals(testCase.getKey(), expected, actual);
    }
  }


  /**
   * Add some more meat to the test case which should not be modified by {@link PlatformDocumentationUtil#fixupText(CharSequence)}.
   */
  private String addPrefixAndSuffix(final String content) {
    return UNMODIFIED_PREFIX + content + UNMODIFIED_SUFFIX;
  }
}
